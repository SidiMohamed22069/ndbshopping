/**
 * Onglet "Importer depuis un site externe" du formulaire d'ajout de produit.
 *
 * Le bouton "Extraire" appelle le proxy Django `product_import_extract`
 * (lui-même un relais vers POST /api/admin/products/import/preview côté
 * Spring Boot) : rien n'est enregistré à cette étape, la réponse sert
 * uniquement à pré-remplir le formulaire classique ci-dessous, que l'admin
 * relit et corrige avant de cliquer sur "Enregistrer".
 */
(function () {
  const modeGroup = document.getElementById("productCreationMode");
  const importPanel = document.getElementById("importPanel");
  const form = document.getElementById("productForm");
  if (!modeGroup || !importPanel || !form) return;

  const csrfInput = form.querySelector("[name=csrfmiddlewaretoken]");
  const urlInput = document.getElementById("importUrl");
  const extractBtn = document.getElementById("importExtractBtn");
  const statusEl = document.getElementById("importStatus");
  const imagesEl = document.getElementById("importImages");
  const importImageUrlsInput = document.getElementById("importImageUrls");

  const nomInput = document.getElementById("nom");
  const descriptionInput = document.getElementById("description");
  const prixInput = document.getElementById("prix");
  const categorySelect = document.getElementById("categoryId");
  const sourceUrlInput = document.getElementById("sourceUrl");
  const sourceOrigineSelect = document.getElementById("sourceOrigine");

  const i18n = {
    loading: importPanel.dataset.msgLoading || "Extraction en cours…",
    fail: importPanel.dataset.msgFail || "Extraction impossible.",
    empty: importPanel.dataset.msgEmpty || "Collez une URL.",
    noImage: importPanel.dataset.msgNoImage || "Aucune image détectée.",
  };

  let extractedImages = [];

  function csrfHeaders() {
    const token = (csrfInput && csrfInput.value) || "";
    return token ? { "X-CSRFToken": token } : {};
  }

  modeGroup.querySelectorAll("[data-mode]").forEach(function (button) {
    button.addEventListener("click", function () {
      modeGroup.querySelectorAll("[data-mode]").forEach(function (b) {
        b.classList.toggle("active", b === button);
      });
      importPanel.hidden = button.dataset.mode !== "import";
      if (button.dataset.mode === "import" && urlInput) {
        urlInput.focus();
      }
    });
  });

  function updateSelectedImages() {
    const checked = Array.from(imagesEl.querySelectorAll("input[type=checkbox]:checked")).map(
      function (input) {
        return input.value;
      }
    );
    importImageUrlsInput.value = JSON.stringify(checked);
  }

  function renderImages(urls) {
    extractedImages = Array.isArray(urls) ? urls : [];
    if (!extractedImages.length) {
      imagesEl.innerHTML = '<p class="text-muted small mb-0">' + i18n.noImage + "</p>";
      importImageUrlsInput.value = "[]";
      return;
    }
    imagesEl.innerHTML = extractedImages
      .map(function (url, index) {
        const id = "importImg" + index;
        return (
          '<label class="import-image-choice" style="display:inline-flex;flex-direction:column;align-items:center;gap:4px;">' +
          '<img src="' + escapeHtml(url) + '" alt="" style="width:96px;height:96px;object-fit:cover;border-radius:6px;border:1px solid var(--bs-border-color,#dee2e6);">' +
          '<span class="form-check">' +
          '<input class="form-check-input" type="checkbox" id="' + id + '" value="' + escapeHtml(url) + '" checked>' +
          '<label class="form-check-label small" for="' + id + '">' + "✓" + "</label>" +
          "</span>" +
          "</label>"
        );
      })
      .join("");
    updateSelectedImages();
  }

  imagesEl.addEventListener("change", function (event) {
    if (event.target && event.target.type === "checkbox") {
      updateSelectedImages();
    }
  });

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function setStatus(message, isError) {
    statusEl.textContent = message || "";
    statusEl.classList.toggle("text-danger", !!isError);
  }

  function selectValue(select, value) {
    if (!select || value === null || value === undefined) return false;
    const stringValue = String(value);
    const option = Array.from(select.options).find(function (opt) {
      return opt.value === stringValue;
    });
    if (!option) return false;
    select.value = stringValue;
    select.dispatchEvent(new Event("change"));
    return true;
  }

  async function extract() {
    const url = (urlInput.value || "").trim();
    if (!url) {
      setStatus(i18n.empty, true);
      return;
    }
    setStatus(i18n.loading, false);
    extractBtn.disabled = true;
    try {
      const body = new URLSearchParams();
      body.set("url", url);
      const response = await fetch(importPanel.dataset.extractUrl, {
        method: "POST",
        headers: Object.assign({ "Content-Type": "application/x-www-form-urlencoded" }, csrfHeaders()),
        body: body.toString(),
        credentials: "same-origin",
      });
      let data = {};
      try {
        data = await response.json();
      } catch (err) {
        data = {};
      }
      if (!response.ok || !data.ok) {
        setStatus((data && data.error) || i18n.fail, true);
        return;
      }
      const item = data.item || {};
      if (nomInput && item.nom) nomInput.value = item.nom;
      if (descriptionInput && item.description) descriptionInput.value = item.description;
      if (prixInput && item.prixOrigine !== null && item.prixOrigine !== undefined) {
        prixInput.value = item.prixOrigine;
      }
      if (sourceUrlInput) sourceUrlInput.value = item.sourceUrl || url;
      selectValue(sourceOrigineSelect, item.sourceOrigine);
      let categoryNote = "";
      if (item.suggestedCategoryId && selectValue(categorySelect, item.suggestedCategoryId)) {
        categoryNote = " — catégorie suggérée : " + (item.suggestedCategoryNom || "");
      }
      renderImages(item.images);
      const priceNote = item.prixOrigine
        ? " Prix source détecté : " + item.prixOrigine + (item.deviseOrigine ? " " + item.deviseOrigine : "") + " (ajustez le prix public ci-dessous)."
        : "";
      setStatus("Extraction réussie." + priceNote + categoryNote, false);
    } catch (err) {
      setStatus(i18n.fail, true);
    } finally {
      extractBtn.disabled = false;
    }
  }

  if (extractBtn) {
    extractBtn.addEventListener("click", extract);
  }
  if (urlInput) {
    urlInput.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();
        extract();
      }
    });
  }
})();
