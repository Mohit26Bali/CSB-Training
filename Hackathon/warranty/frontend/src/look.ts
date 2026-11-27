export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'mosaic' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'wave' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/products': { image: '/banner/products.jpg', title: "Your products", text: "Warranty dates at a glance." },
    '/claims': { image: '/banner/claims.jpg', title: "Your claims", text: "From filed to fixed." },
    '/agent': { image: '/banner/agent.jpg', title: "Claims queue", text: "Oldest and most urgent first." },
  } as Record<string, Banner>,
}
