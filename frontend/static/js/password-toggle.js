/**
 * Ajoute un bouton "afficher / masquer" sur chaque champ mot de passe.
 * Pure amélioration progressive : les formulaires restent fonctionnels sans JS.
 */
(function () {
  document.querySelectorAll('input[type="password"]').forEach(function (input) {
    const wrapper = input.parentElement;
    if (!wrapper || wrapper.querySelector(".password-toggle-btn")) return;

    wrapper.classList.add("has-password-toggle");
    wrapper.style.position = "relative";

    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "password-toggle-btn";
    btn.setAttribute("aria-label", "Afficher le mot de passe");
    btn.innerHTML = '<i class="bi bi-eye" aria-hidden="true"></i>';
    btn.addEventListener("click", function () {
      const showing = input.type === "text";
      input.type = showing ? "password" : "text";
      btn.innerHTML = showing
        ? '<i class="bi bi-eye" aria-hidden="true"></i>'
        : '<i class="bi bi-eye-slash" aria-hidden="true"></i>';
      btn.setAttribute("aria-label", showing ? "Afficher le mot de passe" : "Masquer le mot de passe");
    });
    wrapper.appendChild(btn);
  });
})();
