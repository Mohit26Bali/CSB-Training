export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'slideshow' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'rotate' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'wave' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/courses': { image: '/banner/courses.jpg', title: "Learn something new", text: "Every course in one place, with the seats left." },
    '/my-enrollments': { image: '/banner/my-enrollments.jpg', title: "Your learning", text: "Requests, approvals and what you are taking." },
    '/admin': { image: '/banner/admin.jpg', title: "Training overview", text: "Demand, capacity and requests at a glance." },
    '/admin/courses': { image: '/banner/admin-courses.jpg', title: "Courses", text: "Create, edit and schedule training." },
    '/admin/requests': { image: '/banner/admin-requests.jpg', title: "Requests", text: "Approve or decline, one seat at a time." },
  } as Record<string, Banner>,
}
