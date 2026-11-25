// Wanderlane Admin - charts drawn with SVG (no chart library): area chart, sparkline and donut

const SVG_NS = 'http://www.w3.org/2000/svg';

function niceMax(value) {
    if (value <= 0) {
        return 1;
    }
    const power = 10 ** Math.floor(Math.log10(value));
    const steps = [1, 2, 2.5, 5, 10];
    return steps.map((step) => step * power).find((step) => step >= value);
}

// area chart with hover and keyboard read-out; points = [{ label, value }]
function drawAreaChart(container, points, formatValue, formatAxis) {
    const width = Math.max(container.clientWidth, 280);
    const height = width < 480 ? 200 : 260;
    const pad = { top: 16, right: 12, bottom: 30, left: 54 };
    const innerW = width - pad.left - pad.right;
    const innerH = height - pad.top - pad.bottom;
    const max = niceMax(Math.max(...points.map((point) => point.value)) * 1.1);
    const x = (i) => pad.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW);
    const y = (value) => pad.top + innerH - (value / max) * innerH;

    const grid = [0, 0.25, 0.5, 0.75, 1].map((step) => {
        const yy = y(max * step).toFixed(1);
        return `<line class="grid-line" x1="${pad.left}" x2="${width - pad.right}" y1="${yy}" y2="${yy}"/>
            <text class="axis-label" x="${pad.left - 8}" y="${yy}" text-anchor="end" dominant-baseline="middle">${formatAxis(max * step)}</text>`;
    }).join('');

    const labelEvery = Math.ceil(points.length / (width < 480 ? 4 : 7));
    const xLabels = points.map((point, i) => (i % labelEvery === 0 || i === points.length - 1)
        && !(i !== points.length - 1 && points.length - 1 - i < labelEvery / 2)
        ? `<text class="axis-label" x="${x(i).toFixed(1)}" y="${height - 8}" text-anchor="middle">${point.short}</text>` : '').join('');

    const line = points.map((point, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)} ${y(point.value).toFixed(1)}`).join(' ');
    const area = `${line} L${x(points.length - 1).toFixed(1)} ${y(0)} L${x(0).toFixed(1)} ${y(0)} Z`;

    container.innerHTML = `<svg viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" aria-hidden="true" focusable="false">
        <defs><linearGradient id="area-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="var(--chart-1)" stop-opacity="0.35"/><stop offset="1" stop-color="var(--chart-1)" stop-opacity="0"/>
        </linearGradient></defs>
        ${grid}
        <path class="chart-area-fill" d="${area}" fill="url(#area-fill)"/>
        <path class="chart-line" d="${line}"/>
        ${xLabels}
        <line class="hover-line" x1="0" x2="0" y1="${pad.top}" y2="${pad.top + innerH}" visibility="hidden"/>
        <circle class="hover-dot" r="5" cx="0" cy="0" visibility="hidden"/>
    </svg>
    <div class="chart-tooltip" hidden></div>`;

    const svg = container.querySelector('svg');
    const hoverLine = svg.querySelector('.hover-line');
    const hoverDot = svg.querySelector('.hover-dot');
    const tooltip = container.querySelector('.chart-tooltip');

    function show(index) {
        const point = points[index];
        const cx = x(index);
        const cy = y(point.value);
        hoverLine.setAttribute('x1', cx);
        hoverLine.setAttribute('x2', cx);
        hoverLine.setAttribute('visibility', 'visible');
        hoverDot.setAttribute('cx', cx);
        hoverDot.setAttribute('cy', cy);
        hoverDot.setAttribute('visibility', 'visible');
        tooltip.hidden = false;
        tooltip.innerHTML = `<strong>${formatValue(point.value)}</strong><span>${point.label}</span>`;
        const left = Math.min(Math.max(cx, 70), width - 70);
        tooltip.style.left = `${left}px`;
        tooltip.style.top = `${Math.max(cy - 12, 0)}px`;
        container.dataset.index = index;
        return point;
    }

    function hide() {
        hoverLine.setAttribute('visibility', 'hidden');
        hoverDot.setAttribute('visibility', 'hidden');
        tooltip.hidden = true;
    }

    svg.addEventListener('pointermove', (event) => {
        const box = svg.getBoundingClientRect();
        const px = ((event.clientX - box.left) / box.width) * width;
        const index = Math.round(((px - pad.left) / innerW) * (points.length - 1));
        show(Math.min(Math.max(index, 0), points.length - 1));
    });
    svg.addEventListener('pointerleave', hide);

    return { show, hide, count: points.length };
}

// small trend line for the KPI cards
function sparkline(values, trendUp) {
    const width = 100;
    const height = 32;
    const max = Math.max(...values);
    const min = Math.min(...values);
    const range = max - min || 1;
    const path = values.map((value, i) => `${i === 0 ? 'M' : 'L'}${((i / (values.length - 1)) * width).toFixed(1)} ${(height - 3 - ((value - min) / range) * (height - 6)).toFixed(1)}`).join(' ');
    return `<svg class="sparkline ${trendUp ? 'is-up' : 'is-down'}" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true" focusable="false"><path d="${path}"/></svg>`;
}

// donut chart: segments = [{ label, value, colour }]
function drawDonut(container, segments, centreLabel) {
    const total = segments.reduce((sum, segment) => sum + segment.value, 0) || 1;
    const radius = 15.915;
    let offset = 25;
    const circles = segments.map((segment) => {
        const share = (segment.value / total) * 100;
        const circle = `<circle class="donut-segment" cx="21" cy="21" r="${radius}" stroke="${segment.colour}" stroke-dasharray="${share.toFixed(2)} ${(100 - share).toFixed(2)}" stroke-dashoffset="${offset.toFixed(2)}"/>`;
        offset -= share;
        return circle;
    }).join('');
    container.innerHTML = `<svg viewBox="0 0 42 42" aria-hidden="true" focusable="false">
        <circle class="donut-track" cx="21" cy="21" r="${radius}"/>
        ${circles}
    </svg>
    <p class="donut-centre"><strong>${centreLabel.value}</strong><span>${centreLabel.text}</span></p>`;
}
