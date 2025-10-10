const sliders = document.querySelectorAll('.clued-slider');

sliders.forEach((slider, index) => {
  const flipper = slider.querySelector('.flipper');
  const frontImgs = flipper.querySelectorAll('.front img');
  const backImgs  = flipper.querySelectorAll('.back img');

  let current = 0;
  let angle = 0;

  // показываем первый логотип
  frontImgs[current].classList.add('active');
  backImgs[current].classList.add('active');

  // функция смены логотипа
  function nextLogo() {
    angle += 180;
    flipper.style.transform = `rotateX(${angle}deg)`;

    // смена логотипа в середине поворота
    setTimeout(() => {
      frontImgs[current].classList.remove('active');
      backImgs[current].classList.remove('active');

      current = (current + 1) % frontImgs.length;

      frontImgs[current].classList.add('active');
      backImgs[current].classList.add('active');
    }, 500); // половина transition (1s)
  }

  // ⚡ добавляем индивидуальный задержанный запуск
  const startDelay = index * 200; // 0.5 сек между каждым блоком
  setTimeout(() => {
    setInterval(nextLogo, 4000);
  }, startDelay);
});

