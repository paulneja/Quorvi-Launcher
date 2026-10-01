package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

const ProtocolVersion = 1

type Server struct {
	store     *Store
	dir       string
	workerID  string
	mu        sync.Mutex
	pairCode  string
	pairUntil time.Time
	tokenHash map[string]bool
	processes map[string]*exec.Cmd
	maxOutput int
}

type pairState struct {
	WorkerID string   `json:"workerId"`
	Tokens   []string `json:"tokens"`
}

func NewServer(dir string, store *Store) (*Server, error) {
	s := &Server{store: store, dir: dir, tokenHash: map[string]bool{}, processes: map[string]*exec.Cmd{}, maxOutput: 8 << 20}
	var st pairState
	if data, err := os.ReadFile(filepath.Join(dir, "pairing.json")); err == nil {
		json.Unmarshal(data, &st)
	}
	if st.WorkerID == "" {
		st.WorkerID = randomHex(8)
	}
	s.workerID = st.WorkerID
	for _, t := range st.Tokens {
		s.tokenHash[t] = true
	}
	return s, s.savePairing()
}

func (s *Server) savePairing() error {
	st := pairState{WorkerID: s.workerID}
	for t := range s.tokenHash {
		st.Tokens = append(st.Tokens, t)
	}
	data, _ := json.MarshalIndent(st, "", "  ")
	return os.WriteFile(filepath.Join(s.dir, "pairing.json"), data, 0o600)
}

func (s *Server) NewPairingCode(ttl time.Duration) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pairCode = strings.ToUpper(randomHex(4))
	s.pairUntil = time.Now().Add(ttl)
	return s.pairCode
}

func (s *Server) Revoke() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tokenHash = map[string]bool{}
	s.savePairing()
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/pair", s.pair)
	mux.HandleFunc("GET /v1/hello", s.auth(s.hello))
	mux.HandleFunc("GET /v1/runs", s.auth(s.list))
	mux.HandleFunc("POST /v1/runs", s.auth(s.submit))
	mux.HandleFunc("GET /v1/runs/{id}", s.auth(s.get))
	mux.HandleFunc("GET /v1/runs/{id}/events", s.auth(s.events))
	mux.HandleFunc("POST /v1/runs/{id}/cancel", s.auth(s.cancel))
	return mux
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		sum := sha256.Sum256([]byte(token))
		s.mu.Lock()
		ok := token != "" && s.tokenHash[hex.EncodeToString(sum[:])]
		s.mu.Unlock()
		if !ok {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
			return
		}
		next(w, r)
	}
}

func (s *Server) pair(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Code   string `json:"code"`
		Device string `json:"device"`
	}
	if json.NewDecoder(io.LimitReader(r.Body, 4096)).Decode(&body) != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "bad request"})
		return
	}
	s.mu.Lock()
	valid := s.pairCode != "" && time.Now().Before(s.pairUntil) && subtle.ConstantTimeCompare([]byte(strings.ToUpper(strings.TrimSpace(body.Code))), []byte(s.pairCode)) == 1
	if valid {
		s.pairCode = ""
	}
	s.mu.Unlock()
	if !valid {
		time.Sleep(500 * time.Millisecond)
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "invalid or expired code"})
		return
	}
	token := randomHex(32)
	sum := sha256.Sum256([]byte(token))
	s.mu.Lock()
	s.tokenHash[hex.EncodeToString(sum[:])] = true
	s.savePairing()
	s.mu.Unlock()
	writeJSON(w, http.StatusOK, map[string]any{"token": token, "workerId": s.workerID, "protocol": ProtocolVersion})
}

func (s *Server) hello(w http.ResponseWriter, r *http.Request) {
	host, _ := os.Hostname()
	writeJSON(w, http.StatusOK, map[string]any{"workerId": s.workerID, "hostname": host, "os": runtime.GOOS + "/" + runtime.GOARCH, "protocol": ProtocolVersion, "version": version, "time": time.Now().UTC()})
}

func (s *Server) list(w http.ResponseWriter, r *http.Request) {
	runs := s.store.List()
	if len(runs) > 100 {
		runs = runs[:100]
	}
	writeJSON(w, http.StatusOK, map[string]any{"runs": runs})
}

func (s *Server) submit(w http.ResponseWriter, r *http.Request) {
	var body struct {
		IdempotencyKey string `json:"idempotencyKey"`
		Command        string `json:"command"`
		Cwd            string `json:"cwd"`
		TimeoutSec     int    `json:"timeoutSec"`
	}
	if json.NewDecoder(io.LimitReader(r.Body, 1<<20)).Decode(&body) != nil || strings.TrimSpace(body.Command) == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "command is required"})
		return
	}
	if body.TimeoutSec <= 0 || body.TimeoutSec > 24*3600 {
		body.TimeoutSec = 3600
	}
	run := &Run{ID: randomHex(8), IdempotencyKey: body.IdempotencyKey, Command: body.Command, Cwd: body.Cwd, TimeoutSec: body.TimeoutSec, State: StateRunning, CreatedAt: time.Now().UTC()}
	got, existing, err := s.store.Create(run)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	if !existing {
		go s.execute(got)
	}
	writeJSON(w, http.StatusOK, map[string]any{"run": got, "existing": existing})
}

func (s *Server) get(w http.ResponseWriter, r *http.Request) {
	run, ok := s.store.Get(r.PathValue("id"))
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown run"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"run": run})
}

func (s *Server) events(w http.ResponseWriter, r *http.Request) {
	after, _ := strconv.ParseInt(r.URL.Query().Get("after"), 10, 64)
	wait, _ := strconv.Atoi(r.URL.Query().Get("wait"))
	if wait > 30 {
		wait = 30
	}
	events, run, err := s.store.Events(r.PathValue("id"), after, time.Duration(wait)*time.Second)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
		return
	}
	if events == nil {
		events = []Event{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"events": events, "run": run})
}

func (s *Server) cancel(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	s.mu.Lock()
	cmd := s.processes[id]
	s.mu.Unlock()
	if cmd != nil && cmd.Process != nil {
		syscall.Kill(-cmd.Process.Pid, syscall.SIGTERM)
		time.AfterFunc(3*time.Second, func() { syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL) })
	}
	s.store.Finish(id, StateCancelled, nil)
	run, _ := s.store.Get(id)
	writeJSON(w, http.StatusOK, map[string]any{"run": run})
}

type eventWriter struct {
	s     *Server
	id    string
	typ   string
	total *int
	mu    *sync.Mutex
}

func (e eventWriter) Write(p []byte) (int, error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if *e.total >= e.s.maxOutput {
		return len(p), nil
	}
	*e.total += len(p)
	for _, chunk := range bytes.SplitAfter(p, []byte("\n")) {
		if len(chunk) > 0 {
			e.s.store.Append(e.id, e.typ, string(chunk))
		}
	}
	return len(p), nil
}

func (s *Server) execute(run *Run) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(run.TimeoutSec)*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "/bin/sh", "-c", run.Command)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Cancel = func() error { return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL) }
	if run.Cwd != "" {
		cmd.Dir = run.Cwd
	}
	total, mu := 0, &sync.Mutex{}
	cmd.Stdout = eventWriter{s, run.ID, "stdout", &total, mu}
	cmd.Stderr = eventWriter{s, run.ID, "stderr", &total, mu}
	if err := cmd.Start(); err != nil {
		s.store.Append(run.ID, "stderr", err.Error())
		s.store.Finish(run.ID, StateFailed, nil)
		return
	}
	s.mu.Lock()
	s.processes[run.ID] = cmd
	s.mu.Unlock()
	err := cmd.Wait()
	s.mu.Lock()
	delete(s.processes, run.ID)
	s.mu.Unlock()
	code := 0
	var exitErr *exec.ExitError
	switch {
	case err == nil:
	case errors.As(err, &exitErr):
		code = exitErr.ExitCode()
	default:
		code = -1
	}
	if ctx.Err() == context.DeadlineExceeded {
		s.store.Append(run.ID, "stderr", "timed out\n")
		s.store.Finish(run.ID, StateFailed, &code)
		return
	}
	if code == 0 {
		s.store.Finish(run.ID, StateCompleted, &code)
	} else {
		s.store.Finish(run.ID, StateFailed, &code)
	}
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func randomHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}
