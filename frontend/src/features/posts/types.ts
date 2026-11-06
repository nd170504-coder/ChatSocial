export type PostPrivacy = 'PUBLIC' | 'FRIENDS' | 'PRIVATE'

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
