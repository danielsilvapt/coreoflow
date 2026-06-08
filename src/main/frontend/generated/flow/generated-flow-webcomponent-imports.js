import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import '@vaadin/polymer-legacy-adapter/style-modules.js';
import 'Frontend/generated/jar-resources/vaadin-grid-flow-selection-column.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-column.js';
import '@vaadin/login/theme/lumo/vaadin-login-form.js';
import '@vaadin/tooltip/theme/lumo/vaadin-tooltip.js';
import '@vaadin/icon/theme/lumo/vaadin-icon.js';
import '@vaadin/progress-bar/theme/lumo/vaadin-progress-bar.js';
import '@vaadin/context-menu/theme/lumo/vaadin-context-menu.js';
import 'Frontend/generated/jar-resources/contextMenuConnector.js';
import 'Frontend/generated/jar-resources/contextMenuTargetConnector.js';
import '@vaadin/grid/theme/lumo/vaadin-grid.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-sorter.js';
import '@vaadin/checkbox/theme/lumo/vaadin-checkbox.js';
import 'Frontend/generated/jar-resources/gridConnector.ts';
import '@vaadin/button/theme/lumo/vaadin-button.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/dialog/theme/lumo/vaadin-dialog.js';
import '@vaadin/vertical-layout/theme/lumo/vaadin-vertical-layout.js';
import '@vaadin/horizontal-layout/theme/lumo/vaadin-horizontal-layout.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/grid/theme/lumo/vaadin-grid-column-group.js';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import '@vaadin/combo-box/theme/lumo/vaadin-combo-box.js';
import 'Frontend/generated/jar-resources/comboBoxConnector.js';
import '@vaadin/app-layout/theme/lumo/vaadin-app-layout.js';
import '@vaadin/tabs/theme/lumo/vaadin-tab.js';
import '@vaadin/details/theme/lumo/vaadin-details.js';
import 'Frontend/generated/jar-resources/menubarConnector.js';
import '@vaadin/menu-bar/theme/lumo/vaadin-menu-bar.js';
import '@vaadin/form-layout/theme/lumo/vaadin-form-layout.js';
import '@vaadin/confirm-dialog/theme/lumo/vaadin-confirm-dialog.js';
import '@vaadin/integer-field/theme/lumo/vaadin-integer-field.js';
import '@vaadin/password-field/theme/lumo/vaadin-password-field.js';
import '@vaadin/email-field/theme/lumo/vaadin-email-field.js';
import '@vaadin/upload/theme/lumo/vaadin-upload.js';
import '@vaadin/form-layout/theme/lumo/vaadin-form-item.js';
import '@vaadin/multi-select-combo-box/theme/lumo/vaadin-multi-select-combo-box.js';
import '@vaadin/number-field/theme/lumo/vaadin-number-field.js';
import '@vaadin/text-field/theme/lumo/vaadin-text-field.js';
import '@vaadin/date-picker/theme/lumo/vaadin-date-picker.js';
import 'Frontend/generated/jar-resources/datepickerConnector.js';
import '@vaadin/text-area/theme/lumo/vaadin-text-area.js';
import '@vaadin/app-layout/theme/lumo/vaadin-drawer-toggle.js';
import '@vaadin/tabsheet/theme/lumo/vaadin-tabsheet.js';
import '@vaadin/tabs/theme/lumo/vaadin-tabs.js';
import '@vaadin/select/theme/lumo/vaadin-select.js';
import 'Frontend/generated/jar-resources/selectConnector.js';
import '@vaadin/scroller/theme/lumo/vaadin-scroller.js';
import '@vaadin/time-picker/theme/lumo/vaadin-time-picker.js';
import 'Frontend/generated/jar-resources/vaadin-time-picker/timepickerConnector.js';
import '@vaadin/notification/theme/lumo/vaadin-notification.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import '@vaadin/vaadin-lumo-styles/sizing.js';
import '@vaadin/vaadin-lumo-styles/spacing.js';
import '@vaadin/vaadin-lumo-styles/style.js';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === 'bf69f4029ddcbcd4a25ef3cd121974d3c0ad45b6f17c4b73293c240bd3ca1432') {
    pending.push(import('./chunks/chunk-5d21ef931edb735b0ee70d837ab1d13059cbe461278ce40150095a4e1d2e150b.js'));
  }
  if (key === '6bf75fe9a6051b714b79e568cbec546a71ddf8c2108795ce19a8e12ab172f7a5') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'fc31d18757d06e4b1aa3ea034ea9fdebd070b0e15302c3d46515796f99bbdcea') {
    pending.push(import('./chunks/chunk-cd0beffb1b4dba1b4d6f7afe55e22ae5e6cbe709402a2e845ae429cef710da48.js'));
  }
  if (key === 'b2a581998eefa808c2e86bd0f81cd05c725cac17199d4386a35da449977f2b55') {
    pending.push(import('./chunks/chunk-e9de2f8aac5adb92142db84088fc744a8a62fca6c7f9b25294e181f0c734539b.js'));
  }
  if (key === 'de22798025d49e71cddb0a08be41b152ce0a25bc0cd4367eb8792621fc787b83') {
    pending.push(import('./chunks/chunk-8169d3875a3a136936ce353c16ce33cac0d276b9c060874c516f4c69170a52fb.js'));
  }
  if (key === 'fa344b7226c7cb2518957470d0315b664f589d9e5480e7ef636d71f69bf57fff') {
    pending.push(import('./chunks/chunk-989fa1430fdb4ff326d7ce1091cd60d4a8a560daa813e33b9fa3b73e24f69f0b.js'));
  }
  if (key === '732f626978f64931744e9dd6c7d46415bc24ccb70d6dfddba6f54283c9a58f02') {
    pending.push(import('./chunks/chunk-c7061a26a5624b2a0e184225bfe927a4699b2489bae9c6023c6d8fa545b27b3a.js'));
  }
  if (key === 'c99697d26a6be97e2f4457d2c9b8007bdc93441f3713eabe3275ca3fc87e9c92') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '2663932ae646c0ab6124e9b13d091ce269c2e4ffea2d1cca6cd19e303d10437c') {
    pending.push(import('./chunks/chunk-aa688640de4b2359abad03675254d96030468e33b1e042d6d44192f061d02f6b.js'));
  }
  if (key === 'b02af4e33c3c2b7c5e53102231d6bbf0d9f3d6b8a5bd7387f7f19f1f94324b2b') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '7f335b8956729d4bcc58f3695890817a4a4442e76ee23c07ec4216aa9bc951d4') {
    pending.push(import('./chunks/chunk-aeb02bc8c153c9926d491c3ae4fb4f0da75b994af62767751f2896451c5a24bb.js'));
  }
  if (key === 'a939f67370a2ceb24513a8cfc772b9cb8745ebebce0a7e8080a5c6e7f3dad00a') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'e753c09612bea143f5553aeaadddc63822cdcc807a6a5b8b7b91dae1aef64afe') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '65908168aa01c501e39b947b71fe86fcfc21145ba5e0c6a3bdca81c69f2c4fd6') {
    pending.push(import('./chunks/chunk-7717ca0d5aac402be0912170b95754884c05ebbd44cfd86f5c26e3866eb771ff.js'));
  }
  if (key === '58bc72dd5dd69b8108c5a428c0b5605746d2b266a2b2a057d8bd12904a9630db') {
    pending.push(import('./chunks/chunk-f18b03f048848d21f66740d08d105514d28e4ec8dbf28c7cb4c9cdc1d9c094c3.js'));
  }
  if (key === '26cb452ddeaed8b622534d7a1cb1fa71e000e5b6017577fcd4f6cdbdb4ac2a57') {
    pending.push(import('./chunks/chunk-7448970f91e1c58d9d25f21fdd59b29211c1482acef0b3f7a9648a380d6eeced.js'));
  }
  if (key === '496fa42080a58df766c89aa3e0308c32eea50ad7f97f84c6b527183569e7d070') {
    pending.push(import('./chunks/chunk-f3b58e31fef9cc51d9bf46f921355c718da58eda40dfbfe498a7e2f1b7edb714.js'));
  }
  if (key === '6d8d774ade1961ca921c5f97a0a8da5cbeab10e20e34c1019a4d4b0135a38463') {
    pending.push(import('./chunks/chunk-93b6477dcc83b05a0ebe247d89ddd8d87d21bb98ab1c3ef171cd2ad31320bd87.js'));
  }
  if (key === '2c2d7af06383890e34cde17e45f95d97b573170cf05c34ef4ce36c15faffed7b') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '8ea96984e9beb2af1404e15bc68f5eaee038ebc4b0b84e1ef78fb853681c5b97') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '79dcf26f99ccdc0a3704b6261b8a2ce7bbecfe1e1fb27b99a430dc072236be7a') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '80ff0d6ec9939820e3816c6e3d4e276ed962515c9f4441471e3ca4479bbefc54') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '051c6179e2c5d4a0ca302e3beb1f9a1f551a1004453d331ac7be29747e3ab9f4') {
    pending.push(import('./chunks/chunk-f18b03f048848d21f66740d08d105514d28e4ec8dbf28c7cb4c9cdc1d9c094c3.js'));
  }
  if (key === '66d93958065901037851ba60641db566433d28b86ae86288a0388707514a3c3c') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'f67d70151356486776155886584303285cc7fcacf9893fd160b2921db855b413') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'a23ace970129beee472993c2222fdd1c864175baac2b10d383a9ee7cc310c5d2') {
    pending.push(import('./chunks/chunk-1d03b4d5f74bc4a2c9ebd17c506646e013d5000cd6a79a509cbee6f553d43728.js'));
  }
  if (key === 'db308c69a208a6410c8a6d5d0ebb2c14d040ad9d46c9ff1ba3ba31b64bad7619') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '973de7f7d253814b268d56125b824b9e2fb7058b130060b9de3b57f174234acc') {
    pending.push(import('./chunks/chunk-4086bb19526e625d9c455a582a5637aba6b78c3897c65a4e478077e0bf70a2e6.js'));
  }
  if (key === '4af8bfe4ac729696a18f4e723d9bf68fcc3da86ff4540d437fa73501df219121') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '338bc78ea6ebfab608a44465df0f82bfdb97b1443cebac90d3179e5b974e34c6') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '3c3d14341aecfcf228fc7b58517120b8f6ce0ec4980bf62dd728d17d9814fa56') {
    pending.push(import('./chunks/chunk-a90e6aa3836a9de3c9aa8f67791d23751f318887dd419faa8dd110dc9a615eac.js'));
  }
  if (key === '42948192b1291e9494d8b649d7879d9120a2607d24e266291af95ce05677b08e') {
    pending.push(import('./chunks/chunk-a89a7f2c9c03b0bd977b4a9cfed444af41c1eac164c1e53a40b1388009f234ca.js'));
  }
  if (key === '8b0b84803b0678cbd884bb2df38a358369b55fc1314fc9a0eaafba0debab6605') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '0b2b364f8a2c4ea034cc000bb8bb8018810d9a3ef8375277956cb3a71e0bbaaf') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '2a7ca7be62064b805cd5880110aea5d27171604befbb6655c352ce60cdbdef02') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '47f384fc96b61aafd962f307c2f8b4dbff96a384b0621fa80e4c04bf177ee970') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'bad10bcb367bed58fe0c62ff191e287c0e44af286c47c7b81869b1bbe16c3fde') {
    pending.push(import('./chunks/chunk-4086bb19526e625d9c455a582a5637aba6b78c3897c65a4e478077e0bf70a2e6.js'));
  }
  if (key === '357e60cf1874dcd796f7406d735c7e86b58ba914544866def388960d00f00f2e') {
    pending.push(import('./chunks/chunk-a8830b01a9e6011c630adca0415300ee5bb88293175ab8e305c059e0ec1e02a9.js'));
  }
  if (key === 'b2c83a280f81e7448b2175ee33df9206659d5e7a16c74e48bc02f6ef8f6d017a') {
    pending.push(import('./chunks/chunk-71cdc7407082aba59c233689337e78c347b52f19da824e2632e1a16c8b742b3b.js'));
  }
  if (key === 'e6848019aec0c9d29c30841b87c12d64e1f70511352d06c68fe62e90a90347c0') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'c28a27f1d18d7c8497ba4ce41e8fa6c3fdf657887bc7ac96e28777960d8b3341') {
    pending.push(import('./chunks/chunk-5b0100d06e9137b8f3726f6000e5e516a48da37e5e2058aa2dc9d956fc03390d.js'));
  }
  if (key === '7aa6bd96c467c52d8479b5d1dd766f7f577baa61b16b71afd450f741f0b0370b') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === 'f4bc522bae2f44dc1861a4f079727e5a79859b99030800023a1cd453b74d8793') {
    pending.push(import('./chunks/chunk-6b62f449a2cf8dc06d50fdf353cbbe1fb63fd0980c7556518fa100b03b3eadfe.js'));
  }
  if (key === '207a5fb54d43a01d6f6d9a910f996940ed3f8d3f989eab9220e3acb57655f53a') {
    pending.push(import('./chunks/chunk-c3c66eaa62e6316c568f5de728df26005af37d8022f195bb291e4048f5135363.js'));
  }
  return Promise.all(pending);
}

window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}