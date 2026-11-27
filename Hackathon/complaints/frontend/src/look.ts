export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'mosaic' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/complaints': { image: '/banner/complaints.jpg', title: "Your complaints", text: "Tell us once, follow it to the answer." },
    '/agent': { image: '/banner/agent.jpg', title: "Service overview", text: "Volume, speed and satisfaction." },
    '/agent/queue': { image: '/banner/queue.jpg', title: "The queue", text: "Most urgent, longest waiting first." },
  } as Record<string, Banner>,
}
