// Authentication and editor state exist only for this page session.
export const state = {
    token: null,
    busy: false,
    tab: 'inquiries',
    inquiryPage: 0,
    hospitalPage: 0,
    selectedInquiry: null,
    editingHospital: null,
    replyRequest: null,
    hospitalQuery: '',
    hospitalRequest: null,
    reviewCapabilities: null,
    reviewRequest: null,
};
