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
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/providers': { image: '/banner/providers.jpg', title: "Find a provider", text: "Pick who you want to see." },
    '/appointments': { image: '/banner/appointments.jpg', title: "Your appointments", text: "Requests, confirmations and history." },
    '/provider': { image: '/banner/provider.jpg', title: "Your week", text: "Requests to answer and a calendar to fill." },
    '/provider/calendar': { image: '/banner/provider-calendar.jpg', title: "Calendar", text: "Open slots, bookings and gaps." },
    '/provider/appointments': { image: '/banner/provider-appointments.jpg', title: "Appointments", text: "Confirm or cancel with a reason." },
    '/provider/profile': { image: '/banner/provider-profile.jpg', title: "Your profile", text: "What customers see about you." },
  } as Record<string, Banner>,
}
