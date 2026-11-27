export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'duotone' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'rotate' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'brand' as 'top' | 'brand' | 'side',
  banners: {
    '/jobs': { image: '/banner/jobs.jpg', title: "Open positions", text: "Know someone great? Refer them." },
    '/referrals': { image: '/banner/referrals.jpg', title: "Your referrals", text: "Follow each one through the pipeline." },
    '/recruiter': { image: '/banner/recruiter.jpg', title: "Hiring overview", text: "Where every referral stands." },
    '/recruiter/referrals': { image: '/banner/recruiter-referrals.jpg', title: "Referrals", text: "One stage at a time." },
    '/recruiter/jobs': { image: '/banner/recruiter-jobs.jpg', title: "Jobs", text: "Draft, publish and close roles." },
  } as Record<string, Banner>,
}
