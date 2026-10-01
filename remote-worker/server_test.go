package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type client struct {
	t     *testing.T
	url   string
	token string
}

func (c client) do(method, path string, body any) (int, map[string]any) {
	var buf bytes.Buffer
	if body != nil {
		json.NewEncoder(&buf).Encode(body)
	}
	req, _ := http.NewRequest(method, c.url+path, &buf)
	if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatal(err)
	}
	defer resp.Body.Close()
	var out map[string]any
	json.NewDecoder(resp.Body).Decode(&out)
	return resp.StatusCode, out
}

func setup(t *testing.T, dir string) (*Server, *httptest.Server) {
	store, err := OpenStore(dir)
	if err != nil {
		t.Fatal(err)
	}
	server, err := NewServer(dir, store)
	if err != nil {
		t.Fatal(err)
	}
	return server, httptest.NewServer(server.Handler())
}

func pair(t *testing.T, server *Server, ts *httptest.Server) client {
	code := server.NewPairingCode(time.Minute)
	status, body := client{t: t, url: ts.URL}.do("POST", "/v1/pair", map[string]string{"code": strings.ToLower(code)})
	if status != 200 {
		t.Fatalf("pairing failed: %d %v", status, body)
	}
	return client{t: t, url: ts.URL, token: body["token"].(string)}
}

func waitState(c client, id string) map[string]any {
	for i := 0; i < 100; i++ {
		_, body := c.do("GET", "/v1/runs/"+id, nil)
		run := body["run"].(map[string]any)
		if run["state"] != "RUNNING" {
			return run
		}
		time.Sleep(50 * time.Millisecond)
	}
	return nil
}

func TestPairingIsSingleUseAndTokensAreRequired(t *testing.T) {
	server, ts := setup(t, t.TempDir())
	defer ts.Close()
	anon := client{t: t, url: ts.URL}
	if status, _ := anon.do("GET", "/v1/hello", nil); status != 401 {
		t.Fatalf("hello without token: %d", status)
	}
	code := server.NewPairingCode(time.Minute)
	if status, _ := anon.do("POST", "/v1/pair", map[string]string{"code": "WRONG1"}); status != 403 {
		t.Fatal("wrong code accepted")
	}
	status, body := anon.do("POST", "/v1/pair", map[string]string{"code": code})
	if status != 200 || body["token"] == "" {
		t.Fatal("pairing failed")
	}
	if status, _ := anon.do("POST", "/v1/pair", map[string]string{"code": code}); status != 403 {
		t.Fatal("pairing code reused")
	}
	c := client{t: t, url: ts.URL, token: body["token"].(string)}
	if status, hello := c.do("GET", "/v1/hello", nil); status != 200 || hello["protocol"].(float64) != ProtocolVersion {
		t.Fatalf("hello: %d %v", status, hello)
	}
	server.Revoke()
	if status, _ := c.do("GET", "/v1/hello", nil); status != 401 {
		t.Fatal("revoked token still works")
	}
}

func TestIdempotentRunsAndEventStream(t *testing.T) {
	server, ts := setup(t, t.TempDir())
	defer ts.Close()
	c := pair(t, server, ts)
	_, first := c.do("POST", "/v1/runs", map[string]any{"idempotencyKey": "k1", "command": "echo hola; echo oops >&2; exit 3"})
	id := first["run"].(map[string]any)["id"].(string)
	_, again := c.do("POST", "/v1/runs", map[string]any{"idempotencyKey": "k1", "command": "echo hola; echo oops >&2; exit 3"})
	if again["existing"] != true || again["run"].(map[string]any)["id"] != id {
		t.Fatal("same idempotency key started a second run")
	}
	run := waitState(c, id)
	if run["state"] != "FAILED" || run["exitCode"].(float64) != 3 {
		t.Fatalf("unexpected run %v", run)
	}
	_, events := c.do("GET", "/v1/runs/"+id+"/events?after=0", nil)
	var stdout, stderr string
	var last float64
	for _, e := range events["events"].([]any) {
		ev := e.(map[string]any)
		if ev["seq"].(float64) <= last {
			t.Fatal("events out of order")
		}
		last = ev["seq"].(float64)
		switch ev["type"] {
		case "stdout":
			stdout += ev["data"].(string)
		case "stderr":
			stderr += ev["data"].(string)
		}
	}
	if stdout != "hola\n" || stderr != "oops\n" {
		t.Fatalf("stdout=%q stderr=%q", stdout, stderr)
	}
	_, tail := c.do("GET", "/v1/runs/"+id+"/events?after=2", nil)
	if len(tail["events"].([]any)) >= len(events["events"].([]any)) {
		t.Fatal("after= did not skip delivered events")
	}
}

func TestLongPollWakesOnNewOutputAndCancelStopsTheProcess(t *testing.T) {
	server, ts := setup(t, t.TempDir())
	defer ts.Close()
	c := pair(t, server, ts)
	_, body := c.do("POST", "/v1/runs", map[string]any{"command": "sleep 0.3; echo late; sleep 30"})
	id := body["run"].(map[string]any)["id"].(string)
	start := time.Now()
	_, ev := c.do("GET", "/v1/runs/"+id+"/events?after=1&wait=10", nil)
	if time.Since(start) > 5*time.Second || len(ev["events"].([]any)) == 0 {
		t.Fatal("long poll did not wake on output")
	}
	_, cancelled := c.do("POST", "/v1/runs/"+id+"/cancel", nil)
	if cancelled["run"].(map[string]any)["state"] != "CANCELLED" {
		t.Fatal("cancel did not mark the run")
	}
}

func TestRestartMarksRunningRunsInterrupted(t *testing.T) {
	dir := t.TempDir()
	server, ts := setup(t, dir)
	c := pair(t, server, ts)
	_, body := c.do("POST", "/v1/runs", map[string]any{"idempotencyKey": "long", "command": "sleep 30"})
	id := body["run"].(map[string]any)["id"].(string)
	ts.Close()

	server2, ts2 := setup(t, dir)
	defer ts2.Close()
	c2 := client{t: t, url: ts2.URL, token: c.token}
	_, got := c2.do("GET", "/v1/runs/"+id, nil)
	if got["run"].(map[string]any)["state"] != "INTERRUPTED" {
		t.Fatalf("run after restart: %v", got)
	}
	_, again := c2.do("POST", "/v1/runs", map[string]any{"idempotencyKey": "long", "command": "sleep 30"})
	if again["existing"] != true {
		t.Fatal("idempotency index lost after restart")
	}
	_ = server2
}
