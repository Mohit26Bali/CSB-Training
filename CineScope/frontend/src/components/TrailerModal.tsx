import { Modal } from './Modal'

/**
 * Plays a YouTube trailer in a dialog. {@code videoKey} is the YouTube id from the backend's Video record; null keeps
 * the dialog closed. The privacy-friendly youtube-nocookie.com domain is used, and autoplay starts it right away because
 * the person just pressed a play button.
 */
export function TrailerModal({ videoKey, title, onClose }: { videoKey: string | null; title: string; onClose: () => void }) {
  return (
    <Modal open={videoKey !== null} title={title} onClose={onClose}>
      {videoKey && (
        <div className="aspect-video w-full bg-black">
          <iframe
            title={`${title} (video)`}
            src={`https://www.youtube-nocookie.com/embed/${videoKey}?autoplay=1&rel=0`}
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            allowFullScreen
            className="size-full border-0"
          />
        </div>
      )}
    </Modal>
  )
}
