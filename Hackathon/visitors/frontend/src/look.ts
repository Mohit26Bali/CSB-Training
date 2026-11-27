export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'fade' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'diagonal' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/invitations': { image: '/banner/invitations.jpg', title: "Your visitors", text: "Invite once, they walk straight in." },
    '/gate': { image: '/banner/gate.jpg', title: "The gate", text: "Who is expected, who is inside." },
    '/gate/reports': { image: '/banner/report.jpg', title: "Daily report", text: "Numbers for today and the week." },
  } as Record<string, Banner>,
}
