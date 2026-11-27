export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'mosaic' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'rotate' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'diagonal' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/requests': { image: '/banner/requests.jpg', title: "Your requests", text: "Reported once, tracked to done." },
    '/manager': { image: '/banner/manager.jpg', title: "Community overview", text: "Everything that needs fixing, at a glance." },
    '/manager/requests': { image: '/banner/requests-queue.jpg', title: "Requests", text: "Emergencies first, then the longest waiting." },
    '/manager/technicians': { image: '/banner/technicians.jpg', title: "Technicians", text: "Who can do what, and how busy they are." },
  } as Record<string, Banner>,
}
