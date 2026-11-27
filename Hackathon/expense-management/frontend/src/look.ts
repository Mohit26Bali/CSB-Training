export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'mosaic' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'typewriter' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'diagonal' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/expenses': { image: '/banner/expenses.jpg', title: "Your expenses", text: "Claim it, track it, get paid back." },
    '/manager': { image: '/banner/manager.jpg', title: "Spend overview", text: "Where the team's money goes." },
    '/manager/review': { image: '/banner/review.jpg', title: "Review queue", text: "Approve fairly and keep the budget honest." },
  } as Record<string, Banner>,
}
