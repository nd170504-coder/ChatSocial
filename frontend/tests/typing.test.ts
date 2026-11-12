import { afterEach, expect, it, vi } from 'vitest'
import { createTypingSender, parseTypingEvent } from '../src/features/chat/typing'

afterEach(() => vi.useRealTimers())

it('accepts only well-formed typing events', () => {
  const event = { conversationId: 'chat-1', userId: 'other', typing: true }
  expect(parseTypingEvent(JSON.stringify(event))).toEqual(event)
  for (const input of ['null', 'bad', '{}', JSON.stringify({ ...event, typing: 'true' }), JSON.stringify({ ...event, userId: '' })]) {
    expect(parseTypingEvent(input)).toBeNull()
  }
})

it('throttles typing updates and stops after two idle seconds', () => {
  vi.useFakeTimers()
  const send = vi.fn(() => true), sender = createTypingSender(send)
  sender.input('h')
  vi.advanceTimersByTime(500); sender.input('he')
  vi.advanceTimersByTime(1000); sender.input('hel')
  expect(send.mock.calls).toEqual([[true]])
  vi.advanceTimersByTime(500); sender.input('hello')
  expect(send.mock.calls).toEqual([[true], [true]])
  vi.advanceTimersByTime(1999)
  expect(send).toHaveBeenCalledTimes(2)
  vi.advanceTimersByTime(1)
  expect(send).toHaveBeenLastCalledWith(false)
})

it('stops on empty content or teardown without duplicate stop frames', () => {
  vi.useFakeTimers()
  const send = vi.fn(() => true), sender = createTypingSender(send)
  sender.input('hello'); sender.input(' '); sender.stop()
  expect(send.mock.calls).toEqual([[true], [false]])
  sender.input('again'); sender.stop()
  vi.runAllTimers()
  expect(send.mock.calls).toEqual([[true], [false], [true], [false]])
})

it('does not retain offline activity to replay later', () => {
  vi.useFakeTimers()
  const send = vi.fn(() => false), sender = createTypingSender(send)
  sender.input('offline')
  vi.runAllTimers()
  expect(send.mock.calls).toEqual([[true]])
})
