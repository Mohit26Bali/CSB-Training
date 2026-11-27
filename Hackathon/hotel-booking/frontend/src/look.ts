export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'fade' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'curve' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/rooms': { image: '/banner/rooms.jpg', title: "Find your room", text: "Real availability, real prices, no surprises." },
    '/bookings': { image: '/banner/bookings.jpg', title: "Your stays", text: "Requests, confirmations and memories." },
    '/admin': { image: '/banner/admin.jpg', title: "The hotel tonight", text: "Occupancy, arrivals and revenue." },
    '/admin/bookings': { image: '/banner/admin-bookings.jpg', title: "Bookings", text: "Confirm, release or cancel." },
    '/admin/calendar': { image: '/banner/admin-calendar.jpg', title: "Availability calendar", text: "Every room, every night." },
    '/admin/rooms': { image: '/banner/admin-rooms.jpg', title: "Rooms", text: "Prices, photos and whether they are open." },
  } as Record<string, Banner>,
}
