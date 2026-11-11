import { authenticatedRequest } from '../auth/session'

export interface BlockStatus { userId: string; blockedByMe: boolean; interactionBlocked: boolean }
export interface BlockedUser { userId: string; username: string; fullName: string; avatarUrl: string | null; blockedAt: string }
export interface BlockPage { items: BlockedUser[]; page: number; size: number; hasNext: boolean }
export const blocksApi = {
  status: (id: string, signal?: AbortSignal) => authenticatedRequest<BlockStatus>(`/blocks/${encodeURIComponent(id)}`, { signal }),
  list: (page: number, signal?: AbortSignal) => authenticatedRequest<BlockPage>(`/blocks?page=${page}&size=20`, { signal }),
  change: (id: string, block: boolean) => authenticatedRequest<BlockStatus>(`/blocks/${encodeURIComponent(id)}`, { method: block ? 'PUT' : 'DELETE', signal: AbortSignal.timeout(20_000) }),
}
