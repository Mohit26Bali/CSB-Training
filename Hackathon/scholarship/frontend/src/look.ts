export interface Banner {
  image: string
  title: string
  text: string
}

/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */
export const look = {
  heroStyle: 'slideshow' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',
  headline: 'typewriter' as 'rotate' | 'typewriter' | 'slide' | 'fade',
  edge: 'curve' as 'wave' | 'diagonal' | 'stripes' | 'curve',
  navStyle: 'top' as 'top' | 'brand' | 'side',
  banners: {
    '/programs': { image: '/banner/programs.jpg', title: "Scholarships", text: "Find the ones you qualify for." },
    '/applications': { image: '/banner/applications.jpg', title: "Your applications", text: "Scores, status and decisions." },
    '/admin': { image: '/banner/admin.jpg', title: "Statistics", text: "Applications, awards and budget." },
    '/admin/applications': { image: '/banner/admin-applications.jpg', title: "Applications", text: "Review, score and decide." },
    '/admin/programs': { image: '/banner/admin-programs.jpg', title: "Scholarships", text: "Create and manage programmes." },
  } as Record<string, Banner>,
}
