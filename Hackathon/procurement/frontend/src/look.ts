export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'duotone' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'fade' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'brand' as 'top' | 'brand' | 'side',
  banners: {
    '/requests': { image: '/banner/requests.jpg', title: "Your requests", text: "What you asked for and where it stands." },
    '/manager': { image: '/banner/manager.jpg', title: "Procurement overview", text: "Budget, orders and deliveries." },
    '/manager/review': { image: '/banner/review.jpg', title: "Review queue", text: "Approve, order and track delivery." },
  } as Record<string, Banner>,
}
