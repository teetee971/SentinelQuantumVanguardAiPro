extend: {
  animation: {
    'spin-slow': 'spin 10s linear infinite',
    fadeIn: 'fadeIn 1s ease-in-out',
    float: 'float 8s ease-in-out infinite',
  },
  keyframes: {
    fadeIn: {
      '0%': { opacity: 0 },
      '100%': { opacity: 1 },
    },
    float: {
      '0%': { transform: 'translateY(0px)', opacity: 0.6 },
      '50%': { transform: 'translateY(-20px)', opacity: 1 },
      '100%': { transform: 'translateY(0px)', opacity: 0.6 },
    },
  },
},
