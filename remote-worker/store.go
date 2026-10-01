package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

type RunState string

const (
	StateRunning     RunState = "RUNNING"
	StateCompleted   RunState = "COMPLETED"
	StateFailed      RunState = "FAILED"
	StateCancelled   RunState = "CANCELLED"
	StateInterrupted RunState = "INTERRUPTED"
)

func (s RunState) Terminal() bool { return s != StateRunning }

type Run struct {
	ID             string     `json:"id"`
	IdempotencyKey string     `json:"idempotencyKey"`
	Command        string     `json:"command"`
	Cwd            string     `json:"cwd,omitempty"`
	TimeoutSec     int        `json:"timeoutSec"`
	State          RunState   `json:"state"`
	ExitCode       *int       `json:"exitCode,omitempty"`
	CreatedAt      time.Time  `json:"createdAt"`
	FinishedAt     *time.Time `json:"finishedAt,omitempty"`
	LastSequence   int64      `json:"lastSequence"`
}

type Event struct {
	Seq  int64     `json:"seq"`
	At   time.Time `json:"at"`
	Type string    `json:"type"`
	Data string    `json:"data"`
}

type Store struct {
	dir     string
	mu      sync.Mutex
	runs    map[string]*Run
	byKey   map[string]string
	waiters map[string][]chan struct{}
}

func OpenStore(dir string) (*Store, error) {
	if err := os.MkdirAll(filepath.Join(dir, "runs"), 0o700); err != nil {
		return nil, err
	}
	s := &Store{dir: dir, runs: map[string]*Run{}, byKey: map[string]string{}, waiters: map[string][]chan struct{}{}}
	entries, _ := os.ReadDir(filepath.Join(dir, "runs"))
	for _, e := range entries {
		data, err := os.ReadFile(filepath.Join(dir, "runs", e.Name(), "meta.json"))
		if err != nil {
			continue
		}
		var r Run
		if json.Unmarshal(data, &r) != nil {
			continue
		}

		if r.State == StateRunning {
			r.State = StateInterrupted
			now := time.Now().UTC()
			r.FinishedAt = &now
			s.appendLocked(&r, "state", string(StateInterrupted))
			s.saveMeta(&r)
		}
		s.runs[r.ID] = &r
		if r.IdempotencyKey != "" {
			s.byKey[r.IdempotencyKey] = r.ID
		}
	}
	return s, nil
}

func (s *Store) runDir(id string) string { return filepath.Join(s.dir, "runs", id) }

func (s *Store) saveMeta(r *Run) error {
	data, _ := json.MarshalIndent(r, "", "  ")
	tmp := filepath.Join(s.runDir(r.ID), "meta.json.tmp")
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, filepath.Join(s.runDir(r.ID), "meta.json"))
}

func (s *Store) Create(r *Run) (*Run, bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if r.IdempotencyKey != "" {
		if id, ok := s.byKey[r.IdempotencyKey]; ok {
			return s.runs[id], true, nil
		}
	}
	if err := os.MkdirAll(s.runDir(r.ID), 0o700); err != nil {
		return nil, false, err
	}
	s.runs[r.ID] = r
	if r.IdempotencyKey != "" {
		s.byKey[r.IdempotencyKey] = r.ID
	}
	if err := s.saveMeta(r); err != nil {
		return nil, false, err
	}
	s.appendLocked(r, "state", string(StateRunning))
	return r, false, nil
}

func (s *Store) Get(id string) (*Run, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.runs[id]
	if !ok {
		return nil, false
	}
	copy := *r
	return &copy, true
}

func (s *Store) List() []Run {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]Run, 0, len(s.runs))
	for _, r := range s.runs {
		out = append(out, *r)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.After(out[j].CreatedAt) })
	return out
}

func (s *Store) Append(id, typ, data string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if r, ok := s.runs[id]; ok {
		s.appendLocked(r, typ, data)
	}
}

func (s *Store) appendLocked(r *Run, typ, data string) {
	r.LastSequence++
	ev := Event{Seq: r.LastSequence, At: time.Now().UTC(), Type: typ, Data: data}
	line, _ := json.Marshal(ev)
	f, err := os.OpenFile(filepath.Join(s.runDir(r.ID), "events.jsonl"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600)
	if err == nil {
		f.Write(append(line, '\n'))
		f.Close()
	}
	for _, w := range s.waiters[r.ID] {
		close(w)
	}
	delete(s.waiters, r.ID)
}

func (s *Store) Finish(id string, state RunState, exit *int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, ok := s.runs[id]
	if !ok || r.State.Terminal() {
		return
	}
	now := time.Now().UTC()
	r.State, r.ExitCode, r.FinishedAt = state, exit, &now
	s.appendLocked(r, "state", string(state))
	s.saveMeta(r)
}

func (s *Store) Events(id string, after int64, wait time.Duration) ([]Event, *Run, error) {
	s.mu.Lock()
	r, ok := s.runs[id]
	if !ok {
		s.mu.Unlock()
		return nil, nil, errors.New("unknown run")
	}
	if r.LastSequence <= after && !r.State.Terminal() && wait > 0 {
		ch := make(chan struct{})
		s.waiters[id] = append(s.waiters[id], ch)
		s.mu.Unlock()
		select {
		case <-ch:
		case <-time.After(wait):
		}
		s.mu.Lock()
	}
	copy := *r
	s.mu.Unlock()
	events, err := readEvents(filepath.Join(s.runDir(id), "events.jsonl"), after)
	return events, &copy, err
}

func readEvents(path string, after int64) ([]Event, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var out []Event
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 0, 64*1024), 4*1024*1024)
	for scanner.Scan() {
		var ev Event
		if json.Unmarshal(scanner.Bytes(), &ev) == nil && ev.Seq > after {
			out = append(out, ev)
			if len(out) >= 500 {
				break
			}
		}
	}
	return out, scanner.Err()
}
