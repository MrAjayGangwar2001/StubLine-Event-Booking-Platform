/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          DEFAULT: '#12121A',
          soft: '#1C1D27',
          line: '#2B2C3A',
        },
        paper: {
          DEFAULT: '#F1EFEA',
          muted: '#8B8A99',
        },
        gold: {
          DEFAULT: '#E8A33D',
          soft: '#F4C878',
          dim: '#8A6A2E',
        },
        stub: {
          available: '#2E7D5B',
          locked: '#C98A2E',
          booked: '#3A3B4A',
          selected: '#E8A33D',
        },
        danger: '#D65C5C',
      },
      fontFamily: {
        display: ['"Bebas Neue"', 'sans-serif'],
        body: ['"Inter"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      backgroundImage: {
        perforation:
          'repeating-linear-gradient(to bottom, transparent 0 6px, #2B2C3A 6px 8px)',
      },
    },
  },
  plugins: [],
}
