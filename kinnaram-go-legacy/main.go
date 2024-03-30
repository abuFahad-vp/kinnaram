package main

import (
	"log"
	"net"
)


type MessageType int

const (
  ClientConnected MessageType = iota+1
  NewMessage
  DeleteClient
)

type Message struct {
  Type MessageType
  Conn net.Conn
  Text string
}

func server(messages chan Message) {
  clients := map[string]net.Conn{}
  for {
    client := <- messages
    switch client.Type {
    case ClientConnected:
      log.Printf("Client %s connected to the server\n",client.Conn.RemoteAddr())
      clients[client.Conn.RemoteAddr().String()] = client.Conn
    case DeleteClient:
      delete(clients,client.Conn.LocalAddr().String())
    case NewMessage:
      log.Printf("Client %s said: %s",client.Conn.RemoteAddr(),client.Text)
      for addr,x := range clients {
        if client.Conn.RemoteAddr().String() != addr {
          _,err := x.Write([]byte(client.Text))
          if err != nil {
            log.Printf("Could not sented the message due to %s\n",err)
          }
        }
      }
    }
  }
}

func handleConnection(conn net.Conn, messages chan Message) {
	defer conn.Close()
	buf := make([]byte, 512)
	for {
		n, err := conn.Read(buf)
		if err != nil {
			log.Printf("Could not read buf from %s: %s\n", conn.RemoteAddr(), err)
      messages <- Message{
        Type: DeleteClient,
        Conn: conn,
      }
			return
		}
		messages <- Message{
      Type: NewMessage,
      Conn: conn,
      Text: string(buf[:n]),
    }
	}
}

func main() {
	ln, err := net.Listen("tcp", ":8080")
	if err != nil {
		log.Fatalf("Error: Could not initiate the server: %s\n", err)
	}
	log.Println("Server Started....")
	messages := make(chan Message)
  go server(messages)
	for {
		conn, err := ln.Accept()
		if err != nil {
			log.Printf("Could not connect to the client: %s\n", err)
			continue
		}
		log.Printf("Accepted the connection from %s\n", conn.RemoteAddr())
    messages <- Message{
      Type: ClientConnected,
      Conn: conn,
    }
		go handleConnection(conn, messages)
	}
}
