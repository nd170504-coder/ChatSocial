export function FormFeedback({ error, notice }: { error?: string; notice?: string }) {
  return <>
    {error && <div className="feedback feedback-error" role="alert">{error}</div>}
    {notice && <div className="feedback feedback-success" role="status">{notice}</div>}
  </>
}
