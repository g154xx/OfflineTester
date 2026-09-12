private void showVerdict(ApduAnalyzer.Verdict verdict) {
    switch (verdict) {
        case SUPPORTS_OFFLINE:
            tvVerdict.setText("fonduri insuficiente");
            tvVerdict.setTextColor(getResources().getColor(R.color.green_success));
            ivIcon.setImageResource(R.drawable.ic_green_check);
            break;
        case REQUIRES_ONLINE:
        case DECLINED:
            tvVerdict.setText("fonduri insuficiente");
            tvVerdict.setTextColor(getResources().getColor(R.color.red_error));
            ivIcon.setImageResource(R.drawable.ic_red_x);
            break;
        case INCOMPLETE:
            tvVerdict.setText("Verificare incompletă");
            tvVerdict.setTextColor(getResources().getColor(R.color.yellow_warning));
            ivIcon.setImageResource(R.drawable.ic_yellow_warning);
            break;
        case UNKNOWN:
        default:
            tvVerdict.setText(R.string.status_idle);
            tvVerdict.setTextColor(getResources().getColor(R.color.text_gray));
            ivIcon.setImageDrawable(null);
            break;
    }
}