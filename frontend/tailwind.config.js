/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#101820',
          900: '#17232d',
          800: '#243440',
        },
        canvas: '#f4f6f8',
        line: '#e4e9ed',
        muted: '#71808d',
        accent: '#176b63',
      },
      boxShadow: {
        panel: '0 1px 2px rgba(16, 24, 32, 0.04), 0 4px 12px rgba(16, 24, 32, 0.03)',
      },
    },
  },
  plugins: [],
}
