/**
 * Boîte à idées / avis (widget global, base.html) — soumission en AJAX (fetch),
 * accessible sans connexion. Le formulaire reste fonctionnel sans JS (POST classique).
 */
(function () {
  function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) {
      return parts.pop().split(";").shift();
    }
    return "";
  }

  document.addEventListener("submit", async function (event) {
    const form = event.target;
    if (!form.classList.contains("js-feedback-form")) return;
    event.preventDefault();

    const modalEl = form.closest(".modal");
    const alertEl = form.querySelector(".feedback-form-alert");
    const successEl = form.querySelector(".feedback-form-success");
    const fieldsEl = form.querySelector(".feedback-form-fields");
    const submitBtn = form.querySelector('button[type="submit"]');

    alertEl.classList.add("d-none");
    alertEl.textContent = "";

    const data = new FormData(form);
    submitBtn.disabled = true;

    try {
      const response = await fetch(form.action, {
        method: "POST",
        headers: {
          "X-CSRFToken": data.get("csrfmiddlewaretoken") || getCookie("csrftoken"),
          "X-Requested-With": "XMLHttpRequest",
          Accept: "application/json",
        },
        body: data,
      });
      const payload = await response.json();
      if (payload.ok) {
        fieldsEl.classList.add("d-none");
        successEl.classList.remove("d-none");
        form.querySelector(".modal-footer").classList.add("d-none");
        setTimeout(function () {
          if (modalEl && window.bootstrap) {
            const instance = window.bootstrap.Modal.getOrCreateInstance(modalEl);
            instance.hide();
          }
        }, 1600);
        setTimeout(function () {
          form.reset();
          fieldsEl.classList.remove("d-none");
          successEl.classList.add("d-none");
          form.querySelector(".modal-footer").classList.remove("d-none");
          submitBtn.disabled = false;
        }, 2000);
      } else {
        alertEl.textContent = payload.error || (window.NDB_I18N && window.NDB_I18N.unavailable) || "Erreur";
        alertEl.classList.remove("d-none");
        submitBtn.disabled = false;
      }
    } catch (err) {
      alertEl.textContent = (window.NDB_I18N && window.NDB_I18N.unavailable) || "Service temporairement indisponible";
      alertEl.classList.remove("d-none");
      submitBtn.disabled = false;
    }
  });
})();
