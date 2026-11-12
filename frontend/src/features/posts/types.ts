export type PostPrivacy = 'PUBLIC' | 'FRIENDS' | 'PRIVATE'

export interface PostComment {
  id: string
  postId: string
  userId: string
  fullName: string
  avatarUrl: string | null
  content: string
  createdAt: string
  parentCommentId: string | null
  replyCount: number
  canReply: boolean
}

export interface CommentPage {
  items: PostComment[]
  page: number
  size: number
  hasNext: boolean
}

export interface CommentCount {
  postId: string
  commentCount: number
}

export interface PostLikeStatus {
  postId: string
  likeCount: number
  likedByMe: boolean
}

export interface PostMedia {
  id: string
  url: string
  mediaType: 'IMAGE' | 'VIDEO'
  displayOrder: number
}

export interface Post {
  id: string
  userId: string
  content: string | null
  privacy: PostPrivacy
  createdAt: string
  updatedAt: string
  media: PostMedia[]
  authorName?: string
  authorAvatarUrl?: string | null
}

export interface PostPage {
  items: Post[]
  page: number
  size: number
  hasNext: boolean
}

export interface PostValues {
  content: string | null
  privacy: PostPrivacy
}
