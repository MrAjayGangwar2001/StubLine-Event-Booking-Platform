import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws'

/**
 * Subscribes to live seat status updates for one event over STOMP/WebSocket
 * (with SockJS fallback for networks that block raw WebSockets).
 *
 * Deliberately connection-per-page rather than one global socket for the
 * whole app - simpler lifecycle (connect on mount, disconnect on unmount)
 * and the only place that currently needs live updates is the seat map.
 */
export default function useEventSocket(eventId, onSeatUpdate) {
  const clientRef = useRef(null)
  // Keep the latest callback in a ref so the STOMP subscription (set up once)
  // always calls the current version, without needing to reconnect every
  // time the parent component re-renders with a new inline function.
  const callbackRef = useRef(onSeatUpdate)
  callbackRef.current = onSeatUpdate

  useEffect(() => {
    if (!eventId) return

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 3000, // auto-reconnect if the connection drops (e.g. laptop sleep, wifi blip)
      onConnect: () => {
        client.subscribe(`/topic/event/${eventId}`, (message) => {
          try {
            const update = JSON.parse(message.body)
            callbackRef.current?.(update)
          } catch {
            // Ignore malformed frames rather than crashing the seat map
          }
        })
      },
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [eventId])
}
