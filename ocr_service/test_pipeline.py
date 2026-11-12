import io
from PIL import Image, ImageDraw

def run_test():
    img = Image.new('RGB', (900, 550), color='white')
    draw = ImageDraw.Draw(img)
    lines = [
        'METROPOLIS CLINICAL DIAGNOSTIC LABORATORY',
        'Patient: Test User    Age: 32    Date: 2026-11-09',
        '------------------------------------------------------------',
        'Investigation           Result    Unit      Biological Reference',
        '------------------------------------------------------------',
        'Hemoglobin              14.2      g/dL      13.0 - 17.0',
        'Fasting Blood Sugar     108       mg/dL     70 - 99',
        'HbA1c                   5.8       %         4.0 - 5.6',
        'Total Cholesterol       192       mg/dL     125 - 200',
        'Serum Creatinine        0.95      mg/dL     0.70 - 1.30',
        'Vitamin D Total         22.4      ng/mL     30.0 - 100.0'
    ]
    y = 30
    for l in lines:
        draw.text((30, y), l, fill='black')
        y += 35

    buf = io.BytesIO()
    img.save(buf, format='PNG')
    png_bytes = buf.getvalue()

    # Also save as sample_test_report.png for testing file uploads
    with open('sample_test_report.png', 'wb') as f:
        f.write(png_bytes)

    from ocr_engine import extract_text_from_image
    from parser import parse_lab_parameters
    from ai_advisor import generate_parameter_explanation, generate_report_ai_summary, answer_wellness_question

    text = extract_text_from_image(png_bytes)
    print("=== OCR EXTRACTED TEXT ===")
    print(text.strip())

    print("\n=== PARSED PARAMETERS ===")
    params = parse_lab_parameters(text)
    enriched_list = []
    for p in params:
        enriched = generate_parameter_explanation(p)
        enriched_list.append(enriched)
        print(f"[{enriched['status']}] {enriched['parameter']}: {enriched['value']} {enriched['unit']} (Ref: {enriched['referenceRange']})")
        print(f"    Explanation: {enriched.get('interpretation')}")

    summary = generate_report_ai_summary(enriched_list, 'sample_test_report.png')
    print("\n=== AI SUMMARY ===")
    print("Observations:", summary['keyObservations'])
    print("Recommendations:", summary['wellnessRecommendations'])

    chat_ans = answer_wellness_question("What should I do about my sugar and vitamin D levels?", enriched_list)
    print("\n=== AI CHAT ANSWER ===")
    print(chat_ans)

if __name__ == '__main__':
    run_test()
