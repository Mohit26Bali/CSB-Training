export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'curve' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/leave': { image: '/banner/leave.jpg', title: "Time away", text: "Balances, requests and what is coming up." },
    '/manager/review': { image: '/banner/review.jpg', title: "Leave requests", text: "Decide with the team calendar in view." },
    '/manager/calendar': { image: '/banner/calendar.jpg', title: "Team calendar", text: "Who is out, and when." },
  } as Record<string, Banner>,
}
