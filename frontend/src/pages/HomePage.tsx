import Brand from '../components/Brand';
import ImageUploader from '../components/ImageUploader';

export default function HomePage({ onAnalyze }: { onAnalyze: (file: File, previewUrl: string) => void }) {
  return (
    <div className="page-shell home-shell">
      <header className="site-header"><Brand /><span className="header-note">A more personal kind of skincare</span></header>
      <main>
        <section className="hero-grid">
          <div className="hero-copy">
            <p className="eyebrow"><span className="eyebrow-dot" /> YOUR SKIN, UNDERSTOOD</p>
            <h1>Find your skin’s<br /><em>kind of care.</em></h1>
            <p className="hero-description">A thoughtful skincare routine starts with understanding your skin. Get a personalized analysis and a routine made around you.</p>
            <div className="hero-reassurance"><span className="reassurance-icon">✳</span><span>Personalized insights, made simple</span></div>
          </div>
          <div className="upload-panel" id="upload">
            <div className="upload-panel-heading"><span className="eyebrow">START WITH A SELFIE</span><span className="step-pill">01 / 03</span></div>
            <h2>Let’s get to know your skin</h2>
            <p className="panel-description">Choose a clear, front-facing photo in natural light.</p>
            <ImageUploader onAnalyze={onAnalyze} />
          </div>
        </section>
        <section className="how-it-works" aria-label="How it works">
          <div className="how-intro"><p className="eyebrow">YOUR PERSONAL SKINCARE JOURNEY</p><h2>Simple by nature.</h2></div>
          <div className="journey-steps">
            <div><span className="journey-number">01</span><span className="journey-icon">⌕</span><h3>Understand your skin</h3><p>See the concerns your analysis identifies.</p></div>
            <div><span className="journey-number">02</span><span className="journey-icon">✳</span><h3>Find your routine</h3><p>Explore product matches and daily steps.</p></div>
            <div><span className="journey-number">03</span><span className="journey-icon">♡</span><h3>Care, your way</h3><p>Build a skincare ritual that feels like you.</p></div>
          </div>
        </section>
      </main>
      <footer className="site-footer"><Brand compact /><span>Skincare, thoughtfully personalized.</span><span>For demonstration purposes</span></footer>
    </div>
  );
}
