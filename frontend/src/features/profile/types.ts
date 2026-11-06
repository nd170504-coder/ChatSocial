export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY'

export interface Profile {
  userId: string
  fullName: string
  avatarUrl: string | null
  coverUrl: string | null
  bio: string | null
  dateOfBirth: string | null
  gender: Gender | null
}

export interface UpdateProfileRequest {
  fullName: string
  bio: string | null
  dateOfBirth: string | null
  gender: Gender | null
}

export type ProfileImageType = 'avatar' | 'cover'

export interface UserProfile {
  userId: string
  username: string
  fullName: string
  avatarUrl: string | null
  coverUrl: string | null
  bio: string | null
  relationship: 'SELF' | 'FRIENDS' | 'NONE'
}
