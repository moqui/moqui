(function ($) {
  $(document).ready(function () {
    var resizeTimer,
    $navBarH = $('nav.navbar').height(),
    $headH = $('.head').height(),
    $footerH = $('#footer').height(),
    $innerM = $navBarH + $headH + $footerH + 22;
    function init() {
      $('.inner').css('min-height', function () {
        return 'calc(100vh - ' + $innerM + 'px)';
      });
    }
    init();
    $(window).resize(function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(init(), 250);
    });
  });
})(jQuery);