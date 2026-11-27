export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'slideshow' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'fade' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'curve' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/desk': { image: '/banner/desk.jpg', title: "At the desk", text: "Hand-overs, returns and overdue books." },
    '/desk/loans': { image: '/banner/desk-loans.jpg', title: "Reservations and loans", text: "Issue, take back, collect fines." },
    '/desk/books': { image: '/banner/desk-books.jpg', title: "Books", text: "The catalogue and its copies." },
  } as Record<string, Banner>,
}
