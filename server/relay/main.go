package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

// Envelope representa un mensaje o paquete en tránsito cifrado de punto a punto (E2EE) con padding.
// El servidor NO puede descifrar el payload y NO conoce la identidad real de emisor ni receptor.
type Envelope struct {
	ID          string `json:"id"`
	Tag         string `json:"tag"`
	PayloadHex  string `json:"payloadHex"`
	TimestampMs int64  `json:"timestampMs"`
	ExpiresAt   time.Time
}

type DepositRequest struct {
	Tag         string `json:"tag"`
	ID          string `json:"id"`
	PayloadHex  string `json:"payloadHex"`
	TimestampMs int64  `json:"timestampMs"`
	TTLSeconds  int64  `json:"ttlSeconds"`
}

type AckRequest struct {
	Tag string   `json:"tag"`
	IDs []string `json:"ids"`
}

type FetchResponse struct {
	Envelopes []Envelope `json:"envelopes"`
}

// MailboxStore almacena en memoria los sobres indexados por buzón ciego (tag hex).
type MailboxStore struct {
	mu        sync.RWMutex
	mailboxes map[string][]Envelope
	maxPerTag int
}

func NewMailboxStore(maxPerTag int) *MailboxStore {
	return &MailboxStore{
		mailboxes: make(map[string][]Envelope),
		maxPerTag: maxPerTag,
	}
}

func (s *MailboxStore) Deposit(req DepositRequest) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	ttl := time.Duration(req.TTLSeconds) * time.Second
	if ttl <= 0 || ttl > 7*24*time.Hour {
		ttl = 48 * time.Hour
	}

	env := Envelope{
		ID:          req.ID,
		Tag:         req.Tag,
		PayloadHex:  req.PayloadHex,
		TimestampMs: req.TimestampMs,
		ExpiresAt:   time.Now().Add(ttl),
	}

	queue := s.mailboxes[req.Tag]
	// Evitar duplicados por ID
	for _, existing := range queue {
		if existing.ID == env.ID {
			return nil
		}
	}

	// Limitar tamaño de cola por buzón
	if len(queue) >= s.maxPerTag {
		// Descartar el más antiguo
		queue = queue[1:]
	}

	s.mailboxes[req.Tag] = append(queue, env)
	return nil
}

func (s *MailboxStore) Fetch(tag string, afterTimestampMs int64) []Envelope {
	s.mu.RLock()
	defer s.mu.RUnlock()

	queue, exists := s.mailboxes[tag]
	if !exists {
		return []Envelope{}
	}

	now := time.Now()
	var results []Envelope
	for _, env := range queue {
		if env.ExpiresAt.After(now) && env.TimestampMs > afterTimestampMs {
			results = append(results, env)
		}
	}
	return results
}

func (s *MailboxStore) Ack(tag string, ids []string) int {
	s.mu.Lock()
	defer s.mu.Unlock()

	queue, exists := s.mailboxes[tag]
	if !exists {
		return 0
	}

	idMap := make(map[string]bool, len(ids))
	for _, id := range ids {
		idMap[id] = true
	}

	var remaining []Envelope
	removed := 0
	for _, env := range queue {
		if idMap[env.ID] {
			removed++
		} else {
			remaining = append(remaining, env)
		}
	}

	if len(remaining) == 0 {
		delete(s.mailboxes, tag)
	} else {
		s.mailboxes[tag] = remaining
	}
	return removed
}

func (s *MailboxStore) PurgeExpired() {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	for tag, queue := range s.mailboxes {
		var active []Envelope
		for _, env := range queue {
			if env.ExpiresAt.After(now) {
				active = append(active, env)
			}
		}
		if len(active) == 0 {
			delete(s.mailboxes, tag)
		} else {
			s.mailboxes[tag] = active
		}
	}
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	store := NewMailboxStore(200)

	// Bucle en segundo plano de limpieza de sobres caducados
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		for range ticker.C {
			store.PurgeExpired()
		}
	}()

	mux := http.NewServeMux()

	// Healthcheck
	mux.HandleFunc("/api/v1/relay/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok","network":"arete-blind-relay"}`))
	})

	// Deposit
	mux.HandleFunc("/api/v1/relay/deposit", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}
		var req DepositRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Bad Request", http.StatusBadRequest)
			return
		}
		if req.Tag == "" || req.PayloadHex == "" || req.ID == "" {
			http.Error(w, "Missing required fields", http.StatusBadRequest)
			return
		}

		if err := store.Deposit(req); err != nil {
			http.Error(w, "Internal Server Error", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"deposited"}`))
	})

	// Fetch
	mux.HandleFunc("/api/v1/relay/fetch", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}
		tag := r.URL.Query().Get("tag")
		if tag == "" {
			http.Error(w, "Tag parameter required", http.StatusBadRequest)
			return
		}

		var afterMs int64
		if afterStr := r.URL.Query().Get("after"); afterStr != "" {
			fmt.Sscanf(afterStr, "%d", &afterMs)
		}

		envelopes := store.Fetch(tag, afterMs)
		resp := FetchResponse{Envelopes: envelopes}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	})

	// Ack
	mux.HandleFunc("/api/v1/relay/ack", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
			return
		}
		var req AckRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, "Bad Request", http.StatusBadRequest)
			return
		}

		store.Ack(req.Tag, req.IDs)

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"acknowledged"}`))
	})

	addr := ":" + port
	log.Printf("Aréte Blind Relay Server iniciado en %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Error servidor HTTP: %v", err)
	}
}
