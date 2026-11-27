export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'fade' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'wave' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/events': { image: '/banner/events.jpg', title: "What is on", text: "Grab a seat before it is gone." },
    '/registrations': { image: '/banner/registrations.jpg', title: "Your seats", text: "Registrations and waitlist places." },
    '/admin': { image: '/banner/admin.jpg', title: "Event overview", text: "How full is everything?" },
    '/admin/events': { image: '/banner/admin-events.jpg', title: "Events", text: "Draft, publish and manage." },
  } as Record<string, Banner>,
}
