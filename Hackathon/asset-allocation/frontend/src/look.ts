export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'mosaic' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'typewriter' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'side' as 'top' | 'brand' | 'side',
  banners: {
    '/my-assets': { image: '/banner/my-assets.jpg', title: "My equipment", text: "What IT has handed to you." },
    '/history': { image: '/banner/history.jpg', title: "History", text: "Everything you have handed back." },
    '/admin': { image: '/banner/admin.jpg', title: "Equipment overview", text: "Where everything is." },
    '/admin/assets': { image: '/banner/admin-assets.jpg', title: "Assets", text: "Search, assign, repair, retire." },
    '/admin/returns': { image: '/banner/admin-returns.jpg', title: "Returns", text: "Check equipment back in." },
  } as Record<string, Banner>,
}
