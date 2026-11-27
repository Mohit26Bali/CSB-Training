export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'duotone' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'typewriter' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'brand' as 'top' | 'brand' | 'side',
  banners: {
    '/my-parking': { image: '/banner/my-parking.jpg', title: "Your parking", text: "Your slot and your requests." },
    '/slots': { image: '/banner/slots.jpg', title: "Free slots", text: "Green means free." },
    '/admin': { image: '/banner/admin.jpg', title: "Lot overview", text: "Floor by floor." },
    '/admin/requests': { image: '/banner/admin-requests.jpg', title: "Requests", text: "The queue, oldest first." },
    '/admin/slots': { image: '/banner/admin-slots.jpg', title: "Slot map", text: "The whole lot on one page." },
    '/admin/allocations': { image: '/banner/admin-allocations.jpg', title: "Allocations", text: "Who holds which slot." },
  } as Record<string, Banner>,
}
