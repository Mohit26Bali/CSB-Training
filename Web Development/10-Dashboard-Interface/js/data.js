// Wanderlane Admin - demo data: one list of bookings for the last 400 days; every widget is calculated from it

const PACKAGES = [
    { id: 'japan', name: 'Japan: Temples & Neon', destination: 'Japan', price: 2450, best: [3, 4, 5, 10, 11], colour: '#0f5c6e' },
    { id: 'greek-islands', name: 'Greek Island Hopping', destination: 'Greece', price: 1290, best: [5, 6, 7, 8, 9], colour: '#2a9d8f' },
    { id: 'tanzania-safari', name: 'Tanzania Safari', destination: 'Tanzania', price: 3180, best: [1, 2, 6, 7, 8, 9, 10], colour: '#e9a23b' },
    { id: 'northern-lights', name: 'Iceland Northern Lights', destination: 'Iceland', price: 1150, best: [1, 2, 3, 9, 10, 11, 12], colour: '#6c5ce7' },
    { id: 'maldives', name: 'Maldives Island Escape', destination: 'Maldives', price: 2890, best: [1, 2, 3, 4, 11, 12], colour: '#00a8cc' },
    { id: 'inca-trail', name: 'Peru & the Inca Trail', destination: 'Peru', price: 2690, best: [4, 5, 6, 7, 8, 9, 10], colour: '#c2462a' },
    { id: 'canadian-rockies', name: 'Canadian Rockies Road Trip', destination: 'Canada', price: 1980, best: [6, 7, 8, 9], colour: '#3f8f4f' },
    { id: 'morocco', name: 'Morocco: Medinas & Sahara', destination: 'Morocco', price: 890, best: [3, 4, 5, 9, 10, 11], colour: '#b5656b' }
];

const FIRST_NAMES = ['Amara', 'Ben', 'Chloe', 'Daniel', 'Elif', 'Farah', 'George', 'Hana', 'Isaac', 'Jade', 'Kofi', 'Lena', 'Mateo', 'Nadia', 'Owen', 'Priya',
    'Quinn', 'Rosa', 'Sami', 'Tara', 'Usman', 'Vera', 'Wei', 'Yara', 'Zoe', 'Aditya', 'Bea', 'Carlos', 'Dina', 'Ethan', 'Fatima', 'Gabriel'];
const LAST_INITIALS = 'ABCDEFGHJKLMNOPRSTW';

// small seeded random generator, so the demo data is the same on every visit
function seededRandom(seed) {
    let state = seed >>> 0;
    return () => {
        state = (state + 0x6d2b79f5) >>> 0;
        let t = state;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

function startOfDay(date) {
    const copy = new Date(date);
    copy.setHours(0, 0, 0, 0);
    return copy;
}

function addDays(date, days) {
    const copy = new Date(date);
    copy.setDate(copy.getDate() + days);
    return copy;
}

function buildBookings(today) {
    const random = seededRandom(20261008);
    const bookings = [];
    let number = 4100;

    for (let ago = 399; ago >= 0; ago -= 1) {
        const day = addDays(startOfDay(today), -ago);
        const month = day.getMonth() + 1;
        // more bookings at weekends and in January (the traditional booking peak), with gentle growth over the year
        const weekend = [0, 6].includes(day.getDay()) ? 1.3 : 1;
        const peak = month === 1 ? 1.5 : month === 2 ? 1.2 : 1;
        const growth = 1 + (399 - ago) / 900;
        const count = Math.round((2 + random() * 3) * weekend * peak * growth);

        for (let i = 0; i < count; i += 1) {
            // packages in season (for travel 3 months ahead) are booked more often
            const travelMonth = ((month + 2) % 12) + 1;
            const weights = PACKAGES.map((pack) => (pack.best.includes(travelMonth) ? 3 : 1) * (pack.price < 1500 ? 1.4 : 1));
            let pick = random() * weights.reduce((a, b) => a + b, 0);
            const pack = PACKAGES.find((p, index) => (pick -= weights[index]) < 0) || PACKAGES[0];

            const travellers = 1 + Math.floor(random() * random() * 5);
            const roll = random();
            let status = 'Confirmed';
            if (ago < 10 && roll < 0.35) {
                status = 'Pending';
            } else if (roll > 0.94) {
                status = 'Cancelled';
            }
            number += 1;
            bookings.push({
                id: `WL-${number}`,
                date: new Date(day.getTime() + Math.floor(random() * 14 + 8) * 3600000 + Math.floor(random() * 60) * 60000),
                customer: `${FIRST_NAMES[Math.floor(random() * FIRST_NAMES.length)]} ${LAST_INITIALS[Math.floor(random() * LAST_INITIALS.length)]}.`,
                packageId: pack.id,
                travellers,
                value: pack.price * travellers,
                status
            });
        }
    }
    return bookings;
}

const ENQUIRIES = [
    { id: 'e1', name: 'Grace M.', topic: 'Honeymoon in the Maldives, March', hoursAgo: 1 },
    { id: 'e2', name: 'Rahul P.', topic: 'Family of 5 to Japan in April', hoursAgo: 3 },
    { id: 'e3', name: 'Sofia L.', topic: 'Inca Trail permits for May', hoursAgo: 5 },
    { id: 'e4', name: 'Tom B.', topic: 'Iceland: can we add a glacier lagoon night?', hoursAgo: 9 },
    { id: 'e5', name: 'Yusuf A.', topic: 'Tailor-made Bali trip, 2 weeks', hoursAgo: 22 },
    { id: 'e6', name: 'Elena R.', topic: 'Group of 10 for Morocco', hoursAgo: 30 }
];

const NOTIFICATIONS = [
    { id: 'n1', text: 'Booking WL-PENDING needs a deposit reminder', minutesAgo: 12 },
    { id: 'n2', text: 'Grace M. sent a new enquiry about the Maldives', minutesAgo: 58 },
    { id: 'n3', text: 'Inca Trail permits for May are 80% sold', minutesAgo: 180 },
    { id: 'n4', text: 'Monthly revenue report is ready', minutesAgo: 1440 }
];

const ACTIVITY = [
    { who: 'Leo K.', what: 'confirmed flights for', target: 'Japan: Temples & Neon', minutesAgo: 25 },
    { who: 'Amira K.', what: 'answered an enquiry from', target: 'Hannah W.', minutesAgo: 70 },
    { who: 'Sipho N.', what: 'updated the itinerary for', target: 'Tanzania Safari', minutesAgo: 160 },
    { who: 'Leo K.', what: 'added hotel photos to', target: 'Greek Island Hopping', minutesAgo: 300 },
    { who: 'Mai T.', what: 'published a price change for', target: 'Iceland Northern Lights', minutesAgo: 1500 }
];
