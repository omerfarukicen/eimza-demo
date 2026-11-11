const state = {
    source: 'SMARTCARD',
    terminal: null,
    cardType: null,
    hsmLibrary: null,
    hsmSlot: null,
    certificate: null
};

const $ = (id) => document.getElementById(id);

function toast(message, kind) {
    const el = $('toast');
    el.textContent = message;
    el.className = 'toast ' + (kind || '');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.add('hidden'), kind === 'error' ? 9000 : 4000);
}

async function api(path, options) {
    const response = await fetch(path, options);
    if (!response.ok) {
        let message = response.status + ' ' + response.statusText;
        try {
            const body = await response.json();
            if (body.error) {
                message = body.error;
            }
        } catch (ignored) {
        }
        throw new Error(message);
    }
    return response;
}

async function json(path, options) {
    return (await api(path, options)).json();
}

async function busy(button, action) {
    button.disabled = true;
    try {
        await action();
    } catch (e) {
        toast(e.message, 'error');
    } finally {
        button.disabled = false;
    }
}

function esc(value) {
    return value == null ? '' : String(value)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function date(value) {
    return value ? new Date(value).toLocaleString('tr-TR') : '-';
}

function badge(text, kind) {
    return `<span class="badge ${kind}">${esc(text)}</span>`;
}

async function loadInfo() {
    try {
        const info = await json('/api/info');
        $('info').textContent = `${info.trustedRoots} güvenilir kök, ${info.intermediates} ara sertifika · `
            + `çevrimiçi iptal kontrolü: ${info.onlineRevocation ? 'açık' : 'kapalı'} · `
            + `zaman damgası: ${info.timestampProviders.length ? info.timestampProviders.join(', ') : 'tanımlı değil'} · ${info.os}`;
        const profiles = info.profiles;
        $('profile').innerHTML = profiles.map(p => `<option>${p}</option>`).join('');
        $('extendProfile').innerHTML = profiles.filter(p => /_(T|XLONG|LT|A)$/.test(p)).map(p => `<option>${p}</option>`).join('');
        if (info.hsmLibrary) {
            $('hsmLibrary').value = info.hsmLibrary;
        }
        const types = await json('/api/card-types');
        $('cardType').innerHTML = types.map(t => `<option>${esc(t)}</option>`).join('');
    } catch (e) {
        $('info').textContent = 'Bilgi alınamadı: ' + e.message;
    }
}

function selectSource(source) {
    state.source = source;
    state.certificate = null;
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.source === source));
    document.querySelectorAll('.source').forEach(p => p.classList.toggle('hidden', p.dataset.panel !== source));
    $('certSection').classList.toggle('hidden', source === 'PKCS12');
    $('pin').disabled = source === 'PKCS12';
    renderCertificates([]);
    updateSelected();
}

async function listTerminals() {
    const terminals = await json('/api/terminals');
    const body = $('terminals').querySelector('tbody');
    $('terminals').classList.remove('hidden');
    if (!terminals.length) {
        body.innerHTML = '<tr><td colspan="5" class="muted">Kart okuyucu bulunamadı.</td></tr>';
        return;
    }
    body.innerHTML = terminals.map((t, i) => {
        const types = t.cardTypes.length
            ? t.cardTypes.map(c => badge(c, t.availableCardTypes.includes(c) ? 'ok' : 'warn')).join('')
            : (t.cardPresent ? badge('tanımsız ATR', 'bad') : '-');
        return `<tr data-index="${i}">
            <td><input type="radio" name="terminal" ${t.cardPresent ? '' : 'disabled'}></td>
            <td>${esc(t.name)}</td>
            <td>${t.cardPresent ? badge('takılı', 'ok') : badge('yok', 'warn')}</td>
            <td class="mono">${esc(t.atr || '-')}</td>
            <td>${types}</td></tr>`;
    }).join('');
    body.querySelectorAll('tr[data-index]').forEach(row => {
        const t = terminals[Number(row.dataset.index)];
        const radio = row.querySelector('input');
        radio.addEventListener('change', () => {
            state.terminal = t.name;
            const preferred = t.availableCardTypes[0] || t.cardTypes[0];
            if (preferred) {
                $('cardType').value = preferred;
            }
            body.querySelectorAll('tr').forEach(r => r.classList.toggle('selected', r === row));
        });
    });
    const present = terminals.findIndex(t => t.cardPresent);
    if (present >= 0) {
        const radio = body.querySelector(`tr[data-index="${present}"] input`);
        radio.checked = true;
        radio.dispatchEvent(new Event('change'));
    }
    if (!terminals.some(t => t.cardPresent)) {
        toast('Takılı kart bulunamadı.', 'error');
    }
}

async function listCertificates() {
    if (!state.terminal) {
        throw new Error('Önce bir okuyucu seçin.');
    }
    state.cardType = $('cardType').value;
    const params = new URLSearchParams({terminal: state.terminal, cardType: state.cardType, filter: $('certFilter').value});
    renderCertificates(await json('/api/certificates?' + params));
}

async function listSlots() {
    state.hsmLibrary = $('hsmLibrary').value.trim();
    const slots = await json('/api/hsm/slots?' + new URLSearchParams({library: state.hsmLibrary}));
    $('hsmSlot').innerHTML = slots.map(s => `<option value="${s.id}">${s.id} · ${esc(s.tokenLabel || s.description)}</option>`).join('');
    if (!slots.length) {
        toast('Token içeren slot bulunamadı.', 'error');
    }
}

async function listHsmCertificates() {
    state.hsmLibrary = $('hsmLibrary').value.trim();
    state.hsmSlot = $('hsmSlot').value;
    if (state.hsmSlot === '') {
        throw new Error('Önce slot listeleyin.');
    }
    const params = new URLSearchParams({library: state.hsmLibrary, slot: state.hsmSlot, filter: 'ALL'});
    renderCertificates(await json('/api/hsm/certificates?' + params));
}

function renderCertificates(certificates) {
    const table = $('certificates');
    const body = table.querySelector('tbody');
    state.certificate = null;
    updateSelected();
    table.classList.toggle('hidden', !certificates.length);
    $('certEmpty').classList.toggle('hidden', certificates.length > 0);
    $('certEmpty').textContent = 'Bu filtreyle sertifika bulunamadı.';
    body.innerHTML = certificates.map((c, i) => {
        const tags = [
            c.qualified ? badge('nitelikli', 'ok') : badge('niteliksiz', 'warn'),
            c.nonRepudiation ? badge('imza', 'ok') : '',
            c.maliMuhur ? badge('mali mühür', 'warn') : '',
            c.timeValid ? '' : badge('süresi geçersiz', 'bad')
        ].join('');
        return `<tr data-index="${i}">
            <td><input type="radio" name="certificate"></td>
            <td>${esc(c.commonName)}</td>
            <td>${esc(c.identityNumber || '-')}</td>
            <td class="mono">${esc(c.serialNumberHex)}</td>
            <td>${esc(c.issuerCommonName)}</td>
            <td>${date(c.notBefore)}<br>${date(c.notAfter)}</td>
            <td>${esc(c.keyAlgorithm)} ${c.keySize}</td>
            <td>${tags}</td>
            <td><button data-validate="${i}">Zinciri doğrula</button><div class="validation muted"></div></td></tr>`;
    }).join('');
    body.querySelectorAll('tr[data-index]').forEach(row => {
        const certificate = certificates[Number(row.dataset.index)];
        row.querySelector('input').addEventListener('change', () => {
            state.certificate = certificate;
            body.querySelectorAll('tr').forEach(r => r.classList.toggle('selected', r === row));
            updateSelected();
        });
        const button = row.querySelector('button[data-validate]');
        button.addEventListener('click', () => busy(button, async () => {
            const params = new URLSearchParams({source: state.source, serial: certificate.serialNumberHex});
            if (state.source === 'HSM') {
                params.set('library', state.hsmLibrary);
                params.set('slot', state.hsmSlot);
            } else {
                params.set('terminal', state.terminal);
                params.set('cardType', state.cardType);
            }
            const result = await json('/api/certificates/validate?' + params);
            row.querySelector('.validation').innerHTML = badge(result.indication + (result.subIndication ? ' / ' + result.subIndication : ''),
                result.valid ? 'ok' : (result.indication === 'INDETERMINATE' ? 'warn' : 'bad'));
        }));
    });
    if (certificates.length === 1) {
        const radio = body.querySelector('input');
        radio.checked = true;
        radio.dispatchEvent(new Event('change'));
    }
}

function updateSelected() {
    if (state.source === 'PKCS12') {
        $('selected').textContent = 'PKCS#12 dosyasındaki anahtar kullanılacak.';
        return;
    }
    $('selected').textContent = state.certificate
        ? `Seçili: ${state.certificate.commonName} (${state.certificate.serialNumberHex})`
        : 'Seçili sertifika yok.';
}

function tsFields(form) {
    ['tsUrl', 'tsUser', 'tsPassword'].forEach(id => {
        const value = $(id).value.trim();
        if (value) {
            form.append(id, value);
        }
    });
}

async function download(response, fallback) {
    const blob = await response.blob();
    const disposition = response.headers.get('Content-Disposition') || '';
    const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition) || /filename="?([^";]+)"?/i.exec(disposition);
    const name = match ? decodeURIComponent(match[1]) : fallback;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
    return name;
}

async function sign() {
    const file = $('signFile').files[0];
    if (!file) {
        throw new Error('İmzalanacak belgeyi seçin.');
    }
    const form = new FormData();
    form.append('file', file);
    form.append('source', state.source);
    form.append('profile', $('profile').value);
    form.append('packaging', $('packaging').value);
    form.append('contentIsDigest', $('contentIsDigest').checked);
    if ($('digestAlgorithm').value) {
        form.append('digestAlgorithm', $('digestAlgorithm').value);
    }
    tsFields(form);
    if (state.source === 'PKCS12') {
        const p12 = $('pkcs12').files[0];
        if (!p12) {
            throw new Error('PKCS#12 dosyasını seçin.');
        }
        form.append('pkcs12', p12);
        form.append('pkcs12Password', $('pkcs12Password').value);
    } else {
        if (!state.certificate) {
            throw new Error('İmzalamak için bir sertifika seçin.');
        }
        if (!$('pin').value) {
            throw new Error('PIN girin.');
        }
        form.append('serial', state.certificate.serialNumberHex);
        form.append('pin', $('pin').value);
        if (state.source === 'HSM') {
            form.append('library', state.hsmLibrary);
            form.append('slot', state.hsmSlot);
        } else {
            form.append('terminal', state.terminal);
            form.append('cardType', state.cardType);
        }
    }
    const started = performance.now();
    const response = await api('/api/sign', {method: 'POST', body: form});
    const name = await download(response, 'imzali');
    toast(`İmzalandı: ${name} (${Math.round(performance.now() - started)} ms)`, 'success');
    $('pin').value = '';
}

async function verify() {
    const signed = $('verifySigned').files[0];
    if (!signed) {
        throw new Error('Doğrulanacak imzalı dosyayı seçin.');
    }
    const form = new FormData();
    form.append('signed', signed);
    const original = $('verifyOriginal').files[0];
    if (original) {
        form.append('original', original);
    }
    form.append('originalIsDigest', $('verifyOriginalIsDigest').checked);
    form.append('rejectMaliMuhur', $('rejectMaliMuhur').checked);
    renderVerification(await json('/api/verify', {method: 'POST', body: form}));
}

function renderVerification(result) {
    const signatures = result.signatures.map((s, i) => {
        const kind = s.valid ? 'ok' : (s.indication === 'INDETERMINATE' ? 'warn' : 'bad');
        const list = (items, cls) => items.length ? `<ul>${items.map(x => `<li class="${cls}">${esc(x)}</li>`).join('')}</ul>` : '-';
        return `<div class="sig">
            <div>${badge(s.valid ? 'GEÇERLİ' : 'GEÇERSİZ', kind)} ${badge(s.indication + (s.subIndication ? ' / ' + s.subIndication : ''), kind)}
                <strong>#${i + 1} ${esc(s.format)}</strong></div>
            <dl>
                <dt>İmzalayan</dt><dd>${esc(s.signerName)} ${s.signerIdentityNumber ? '(' + esc(s.signerIdentityNumber) + ')' : ''}</dd>
                <dt>İmza zamanı</dt><dd>${date(s.signingTime)}</dd>
                <dt>En iyi imza zamanı</dt><dd>${date(s.bestSignatureTime)}</dd>
                <dt>Sertifika seri no</dt><dd class="mono">${esc(s.certificateSerialHex)}</dd>
                <dt>Sertifikayı veren</dt><dd>${esc(s.issuerName)}</dd>
                <dt>Sertifika geçerliliği</dt><dd>${date(s.certificateNotBefore)} – ${date(s.certificateNotAfter)}</dd>
                <dt>Algoritma</dt><dd>${esc(s.signatureAlgorithm)} (${esc(s.digestAlgorithm)})</dd>
                <dt>Sertifika</dt><dd>${s.qualifiedCertificate ? badge('nitelikli', 'ok') : badge('niteliksiz', 'warn')}${s.maliMuhur ? badge('mali mühür', 'warn') : ''}</dd>
                <dt>Kural ihlalleri</dt><dd>${list(s.ruleViolations, '')}</dd>
                <dt>Hatalar</dt><dd>${list(s.errors, '')}</dd>
                <dt>Uyarılar</dt><dd>${list(s.warnings, 'muted')}</dd>
            </dl>
        </div>`;
    }).join('');
    const content = result.contentText != null
        ? `<details><summary>İmzalanan içerik (${result.contentSize} byte)</summary><pre>${esc(result.contentText)}</pre></details>`
        : (result.contentSize != null ? `<p class="muted">İmzalanan içerik: ${result.contentSize} byte (ikili)</p>` : '');
    $('verifyResult').innerHTML = `
        <div class="result-head">${badge(result.valid ? 'GEÇERLİ' : 'GEÇERSİZ', result.valid ? 'ok' : 'bad')} ${esc(result.summary)}</div>
        ${signatures}
        ${content}
        <details><summary>DSS basit rapor (XML)</summary><pre>${esc(result.simpleReportXml)}</pre></details>`;
}

async function extend() {
    const signed = $('extendSigned').files[0];
    if (!signed) {
        throw new Error('Yükseltilecek imzalı dosyayı seçin.');
    }
    const form = new FormData();
    form.append('signature', signed);
    form.append('profile', $('extendProfile').value);
    const original = $('extendOriginal').files[0];
    if (original) {
        form.append('original', original);
    }
    form.append('originalIsDigest', $('extendOriginalIsDigest').checked);
    tsFields(form);
    const response = await api('/api/extend', {method: 'POST', body: form});
    const name = await download(response, 'yukseltilmis');
    toast('Yükseltildi: ' + name, 'success');
}

document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => selectSource(tab.dataset.source)));
$('btnTerminals').addEventListener('click', e => busy(e.currentTarget, listTerminals));
$('btnCertificates').addEventListener('click', e => busy(e.currentTarget, listCertificates));
$('btnSlots').addEventListener('click', e => busy(e.currentTarget, listSlots));
$('btnHsmCertificates').addEventListener('click', e => busy(e.currentTarget, listHsmCertificates));
$('btnSign').addEventListener('click', e => busy(e.currentTarget, sign));
$('btnVerify').addEventListener('click', e => busy(e.currentTarget, verify));
$('btnExtend').addEventListener('click', e => busy(e.currentTarget, extend));

loadInfo();
selectSource('SMARTCARD');
