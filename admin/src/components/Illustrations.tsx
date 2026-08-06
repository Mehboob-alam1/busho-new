type IllustProps = { className?: string; title?: string };

/** Soft scene illustrations — no external assets. */
export function IllustLogin({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 320 180" fill="none" aria-hidden>
      <rect width="320" height="180" rx="24" fill="#F3EEE4" />
      <circle cx="250" cy="42" r="28" fill="#D9EDE8" />
      <circle cx="60" cy="150" r="40" fill="#E8DFD0" />
      <rect x="78" y="48" width="120" height="84" rx="16" fill="#FFFDF9" stroke="#D6CFC1" />
      <rect x="94" y="66" width="56" height="8" rx="4" fill="#C9D8D4" />
      <rect x="94" y="84" width="88" height="8" rx="4" fill="#E3DACB" />
      <rect x="94" y="102" width="70" height="8" rx="4" fill="#E3DACB" />
      <rect x="188" y="70" width="54" height="54" rx="14" fill="#0F6B5C" />
      <path d="M206 97l8 8 16-18" stroke="#FFFDF9" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="120" cy="36" r="10" fill="#B8A48A" opacity="0.45" />
    </svg>
  );
}

export function IllustEmptyUsers({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 240 150" fill="none" aria-hidden>
      <rect width="240" height="150" rx="20" fill="#F4EFE6" />
      <circle cx="96" cy="62" r="18" fill="#C9D8D4" />
      <path d="M66 108c4-18 16-28 30-28s26 10 30 28" fill="#E2D8C8" />
      <circle cx="152" cy="58" r="14" fill="#0F6B5C" opacity="0.85" />
      <path d="M128 108c3-14 12-22 24-22s21 8 24 22" fill="#D9EDE8" />
      <rect x="48" y="118" width="144" height="10" rx="5" fill="#E0D7C8" />
    </svg>
  );
}

export function IllustEmptyVideos({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 240 150" fill="none" aria-hidden>
      <rect width="240" height="150" rx="20" fill="#F4EFE6" />
      <rect x="52" y="40" width="110" height="74" rx="14" fill="#FFFDF9" stroke="#D6CFC1" />
      <path d="M108 66l22 14-22 14V66z" fill="#0F6B5C" />
      <rect x="174" y="52" width="22" height="50" rx="8" fill="#C9D8D4" />
      <circle cx="185" cy="118" r="8" fill="#B8A48A" opacity="0.5" />
    </svg>
  );
}

export function IllustEmptyFeed({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 240 150" fill="none" aria-hidden>
      <rect width="240" height="150" rx="20" fill="#F4EFE6" />
      <rect x="40" y="36" width="160" height="28" rx="10" fill="#FFFDF9" stroke="#D6CFC1" />
      <rect x="40" y="74" width="130" height="28" rx="10" fill="#FFFDF9" stroke="#D6CFC1" />
      <rect x="40" y="112" width="148" height="18" rx="9" fill="#E2D8C8" />
      <circle cx="196" cy="50" r="10" fill="#0F6B5C" opacity="0.8" />
    </svg>
  );
}

export function IllustEmptyGallery({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 240 150" fill="none" aria-hidden>
      <rect width="240" height="150" rx="20" fill="#F4EFE6" />
      <rect x="38" y="38" width="70" height="54" rx="12" fill="#FFFDF9" stroke="#D6CFC1" />
      <rect x="118" y="38" width="84" height="54" rx="12" fill="#D9EDE8" />
      <rect x="38" y="102" width="164" height="22" rx="11" fill="#E2D8C8" />
      <circle cx="60" cy="58" r="8" fill="#B8A48A" opacity="0.55" />
      <path d="M48 82l14-12 12 8 16-14 12 12" stroke="#0F6B5C" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

export function IllustUpload({ className }: IllustProps) {
  return (
    <svg className={className} viewBox="0 0 120 88" fill="none" aria-hidden>
      <rect width="120" height="88" rx="18" fill="#EAF5F2" />
      <path d="M60 58V28" stroke="#0F6B5C" strokeWidth="3.5" strokeLinecap="round" />
      <path d="M46 40l14-14 14 14" stroke="#0F6B5C" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M30 66h60" stroke="#9FBFB7" strokeWidth="3.5" strokeLinecap="round" />
    </svg>
  );
}
