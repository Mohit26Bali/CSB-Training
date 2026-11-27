export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'duotone' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'typewriter' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'wave' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/tickets': { image: '/banner/tickets.jpg', title: "Your tickets", text: "Raise it once, follow it to the fix." },
    '/agent': { image: '/banner/agent.jpg', title: "Support overview", text: "Deadlines, load and what is waiting." },
    '/agent/queue': { image: '/banner/queue.jpg', title: "The queue", text: "Most urgent first." },
  } as Record<string, Banner>,
}
