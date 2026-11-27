export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'slideshow' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'brand' as 'top' | 'brand' | 'side',
  banners: {
    '/services': { image: '/banner/services.jpg', title: "Your services", text: "From booked to ready, with the bill." },
    '/book': { image: '/banner/book.jpg', title: "Book a service", text: "Pick the work, pick a free day." },
    '/vehicles': { image: '/banner/vehicles.jpg', title: "Your vehicles", text: "Every car and bike in one place." },
    '/admin': { image: '/banner/admin.jpg', title: "The workshop today", text: "Bays, arrivals and takings." },
    '/admin/schedule': { image: '/banner/admin-schedule.jpg', title: "Schedule", text: "Start, finish and cancel services." },
    '/admin/vehicles': { image: '/banner/admin-vehicles.jpg', title: "Vehicles", text: "Find any vehicle and its history." },
    '/admin/services': { image: '/banner/admin-services.jpg', title: "Services", text: "What we offer and what it costs." },
  } as Record<string, Banner>,
}
