export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'kenburns' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'slide' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'stripes' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/menu': { image: '/banner/menu.jpg', title: "Today's menu", text: "Fresh from the tandoor." },
    '/orders': { image: '/banner/orders.jpg', title: "Your orders", text: "From the kitchen to your door." },
    '/admin': { image: '/banner/admin.jpg', title: "The kitchen", text: "New, cooking, ready." },
    '/admin/menu': { image: '/banner/admin-menu.jpg', title: "The menu", text: "Switch dishes on and off." },
    '/admin/sales': { image: '/banner/admin-sales.jpg', title: "Sales", text: "What sold, and when." },
  } as Record<string, Banner>,
}
