/**
 * Retour visuel immédiat à la soumission d'un formulaire (bouton désactivé +
 * spinner) pour éviter les doubles clics et rassurer l'utilisateur pendant
 * le chargement de la page suivante.
 *
 * Exclus : .js-add-to-cart (géré en AJAX par cart.js, qui ne recharge pas la
 * page — désactiver le bouton ici le laisserait bloqué) et tout formulaire
 * marqué data-no-spinner.
 */
(function () {
  document.addEventListener("submit", function (event) {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.classList.contains("js-add-to-cart") || form.dataset.noSpinner !== undefined) return;

    const btn = form.querySelector('button[type="submit"]');
    if (!btn || btn.disabled) return;

    btn.disabled = true;
    btn.classList.add("btn-loading");
    btn.dataset.originalHtml = btn.innerHTML;
    btn.innerHTML =
      '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> ' + btn.innerHTML;
  });
})();
