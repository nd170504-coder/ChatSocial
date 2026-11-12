import { authenticatedBlobRequest, authenticatedRequest } from '../auth/session'
import type { CommentCount, CommentPage, Post, PostComment, PostLikeStatus, PostPage, PostValues } from './types'

export const postApi = {
  replies: (postId: string, parentId: string, page = 0, signal?: AbortSignal) => authenticatedRequest<CommentPage>(
    `/post/${encodeURIComponent(postId)}/comments/${encodeURIComponent(parentId)}/replies?page=${page}&size=10`, { signal },
  ),
  createReply: (postId: string, parentId: string, content: string) => authenticatedRequest<PostComment>(
    `/post/${encodeURIComponent(postId)}/comments/${encodeURIComponent(parentId)}/replies`, { method: 'POST', body: JSON.stringify({ content }) },
  ),
  comments: (postId: string, page = 0, signal?: AbortSignal) => authenticatedRequest<CommentPage>(
    `/post/${encodeURIComponent(postId)}/comments?page=${page}&size=10`, { signal },
  ),
  commentCount: (postId: string, signal?: AbortSignal) => authenticatedRequest<CommentCount>(
    `/post/${encodeURIComponent(postId)}/comments/count`, { signal },
  ),
  createComment: (postId: string, content: string) => authenticatedRequest<PostComment>(
    `/post/${encodeURIComponent(postId)}/comments`, { method: 'POST', body: JSON.stringify({ content }) },
  ),
  deleteComment: (postId: string, commentId: string) => authenticatedRequest<CommentCount>(
    `/post/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' },
  ),
  likes: (postId: string, signal?: AbortSignal) => authenticatedRequest<PostLikeStatus>(
    `/post/${encodeURIComponent(postId)}/likes`, { signal },
  ),
  like: (postId: string) => authenticatedRequest<PostLikeStatus>(
    `/post/${encodeURIComponent(postId)}/likes`, { method: 'PUT' },
  ),
  unlike: (postId: string) => authenticatedRequest<PostLikeStatus>(
    `/post/${encodeURIComponent(postId)}/likes`, { method: 'DELETE' },
  ),
  feed: (page = 0, signal?: AbortSignal) => authenticatedRequest<PostPage>(
    `/post/feed?page=${page}&size=10`, { signal },
  ),
  list: (userId: string, page = 0, signal?: AbortSignal) => authenticatedRequest<PostPage>(
    `/users/${encodeURIComponent(userId)}/posts?page=${page}&size=10`, { signal },
  ),
  get: (postId: string, signal?: AbortSignal) => authenticatedRequest<Post>(
    `/post/${encodeURIComponent(postId)}`, { signal },
  ),
  create: (values: PostValues, files: File[]) => {
    const body = new FormData()
    body.append('post', new Blob([JSON.stringify(values)], { type: 'application/json' }))
    files.forEach(file => body.append('files', file))
    return authenticatedRequest<Post>('/post', { method: 'POST', body })
  },
  update: (postId: string, values: PostValues) => authenticatedRequest<Post>(
    `/post/${encodeURIComponent(postId)}`, { method: 'PATCH', body: JSON.stringify(values) },
  ),
  remove: (postId: string) => authenticatedRequest<null>(
    `/post/${encodeURIComponent(postId)}`, { method: 'DELETE' },
  ),
  // Build a trusted relative API path instead of attaching tokens to arbitrary media URLs.
  image: (postId: string, mediaId: string, signal?: AbortSignal) => authenticatedBlobRequest(
    `/post/${encodeURIComponent(postId)}/media/${encodeURIComponent(mediaId)}`, { signal },
  ),
}
