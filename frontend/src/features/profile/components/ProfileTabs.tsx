import { Link, useLocation } from 'react-router'
import { Icon } from '../../../components/ui/Icon'

export function ProfileTabs() {
  const { hash } = useLocation()
  const about = hash === '#about'
  return <nav className="profile-section-nav" aria-label="Nội dung hồ sơ">
    <Link to="#posts" replace preventScrollReset aria-current={!about ? 'page' : undefined}><Icon name="edit" />Bài viết</Link>
    <Link to="#about" replace preventScrollReset aria-current={about ? 'page' : undefined}><Icon name="user" />Giới thiệu</Link>
  </nav>
}
