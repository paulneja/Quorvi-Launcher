package main

import (
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

const version = "0.1.0"

func main() {
	home, _ := os.UserHomeDir()
	listen := flag.String("listen", "127.0.0.1:8790", "address to listen on (use a Tailscale or LAN address to reach it from the phone)")
	dir := flag.String("data", filepath.Join(home, ".local", "share", "quorvi-worker"), "data directory")
	certFile := flag.String("tls-cert", "", "TLS certificate (PEM)")
	keyFile := flag.String("tls-key", "", "TLS private key (PEM)")
	pairTTL := flag.Duration("pair-ttl", 10*time.Minute, "how long a pairing code stays valid")
	revoke := flag.Bool("revoke", false, "revoke every paired phone and exit")
	flag.Parse()

	store, err := OpenStore(*dir)
	if err != nil {
		log.Fatal(err)
	}
	server, err := NewServer(*dir, store)
	if err != nil {
		log.Fatal(err)
	}
	if *revoke {
		server.Revoke()
		fmt.Println("All paired devices were revoked.")
		return
	}
	code := server.NewPairingCode(*pairTTL)
	fmt.Printf("Quorvi worker %s listening on %s\nPairing code (valid %s): %s\n", version, *listen, *pairTTL, code)
	srv := &http.Server{Addr: *listen, Handler: server.Handler(), ReadHeaderTimeout: 10 * time.Second}
	if *certFile != "" {
		log.Fatal(srv.ListenAndServeTLS(*certFile, *keyFile))
	}
	log.Fatal(srv.ListenAndServe())
}
