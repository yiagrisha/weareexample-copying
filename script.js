const sliders = document.querySelectorAll('.clued-slider')

sliders.forEach((slider, index) => {
  const flipper = slider.querySelector('.flipper')
  const frontImgs = flipper.querySelectorAll('.front img')
  const backImgs  = flipper.querySelectorAll('.back img')

  let current = 0
  let angle = 0

  frontImgs[current].classList.add('active')
  backImgs[current].classList.add('active')

  function nextLogo() {
    angle += 180
    flipper.style.transform = `rotateX(${angle}deg)`

    setTimeout(() => {
      frontImgs[current].classList.remove('active')
      backImgs[current].classList.remove('active')

      current = (current + 1) % frontImgs.length

      frontImgs[current].classList.add('active')
      backImgs[current].classList.add('active')
    }, 500)
  }

  const startDelay = index * 200
  setTimeout(() => {
    setInterval(nextLogo, 4000)
  }, startDelay)
})

document.addEventListener("DOMContentLoaded", () => {
  const sliders = document.querySelectorAll(".header-slider")

  sliders.forEach(slider => {
    const masks = slider.querySelectorAll(".mask")

    let current = 0;
    let z = 1;

    masks[current].style.height = "100%"
    masks[current].style.zIndex = z
    const firstImg = masks[current].querySelector("img")
    if (firstImg) firstImg.style.transform = "scale(1)"

    function nextSlide() {
      const next = (current + 1) % masks.length
      z++

      const nextMask = masks[next]
      const nextImg = nextMask.querySelector("img")
      const currentMask = masks[current]

      nextMask.style.height = "0%"
      nextMask.style.zIndex = z

      if (nextImg) {
        nextImg.style.transform = "scale(2)"
        nextImg.style.transition = "transform 1.5s ease-in-out"
      }

      requestAnimationFrame(() => {
        nextMask.style.transition = "height 1.5s ease-in-out"
        nextMask.style.height = "100%"

        if (nextImg) {
          nextImg.style.transform = "scale(1)"
        }
      })

      current = next

      const afterNext = (next + 1) % masks.length
      masks[afterNext].style.height = "0%"
    }

    setInterval(nextSlide, 4000)
  })
})
