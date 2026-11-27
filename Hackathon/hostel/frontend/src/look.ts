export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'diagonal' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/my-room': { image: '/banner/my-room.jpg', title: "Your room", text: "Your bed and your requests." },
    '/rooms': { image: '/banner/rooms.jpg', title: "Free rooms", text: "Beds that are ready to move into." },
    '/admin': { image: '/banner/admin.jpg', title: "Occupancy", text: "Beds taken and beds free." },
    '/admin/requests': { image: '/banner/admin-requests.jpg', title: "Requests", text: "The waiting list, oldest first." },
    '/admin/rooms': { image: '/banner/admin-rooms.jpg', title: "Rooms", text: "Every room and who lives in it." },
    '/admin/residents': { image: '/banner/admin-residents.jpg', title: "Residents", text: "Who lives where." },
  } as Record<string, Banner>,
}
