"""
AI Integration and Clinical Biomarker Advisor for My Wellness.
Provides plain-English explanations of extracted laboratory parameters,
distinguishing normal, low, and high values using the original printed reference intervals.
Provides general lifestyle and wellness recommendations.
Enforces strict medical disclaimers and safety guardrails:
- Does not diagnose diseases.
- Does not prescribe medications or medical treatments.
- Does not invent or hallucinate missing laboratory values.
"""

import logging
import json
import os
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

logger = logging.getLogger("ocr_service.ai_advisor")

OLLAMA_URL = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
QWEN_MODEL = os.getenv("QWEN_MODEL", "qwen2.5:7b")
QWEN_TIMEOUT = float(os.getenv("QWEN_TIMEOUT_SECONDS", "45"))


def qwen_status() -> Dict[str, Any]:
    """Return local Ollama/Qwen availability for the service health endpoint."""
    request = urllib.request.Request(f"{OLLAMA_URL}/api/tags", method="GET")
    try:
        with urllib.request.urlopen(request, timeout=1.5) as response:
            payload = json.loads(response.read().decode("utf-8"))
        names = [item.get("name", "") for item in payload.get("models", [])]
        installed = any(name == QWEN_MODEL or name.startswith(QWEN_MODEL + ":") for name in names)
        return {"available": installed, "provider": "ollama", "model": QWEN_MODEL}
    except Exception:
        return {"available": False, "provider": "ollama", "model": QWEN_MODEL}


def _qwen_chat(system_prompt: str, user_prompt: str, json_output: bool = False) -> Optional[str]:
    """Call a local Ollama model. Fail closed to the deterministic guidance below."""
    payload: Dict[str, Any] = {
        "model": QWEN_MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "options": {"temperature": 0.2},
    }
    if json_output:
        payload["format"] = "json"
    request = urllib.request.Request(
        f"{OLLAMA_URL}/api/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=QWEN_TIMEOUT) as response:
            result = json.loads(response.read().decode("utf-8"))
        content = result.get("message", {}).get("content", "").strip()
        return content or None
    except (OSError, ValueError, urllib.error.URLError) as exc:
        logger.info("Qwen unavailable; using built-in guidance: %s", exc)
        return None


def _qwen_report_summary(parameters: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    system = (
        "You explain laboratory report results for general education. You are not a doctor. "
        "Never diagnose a disease, infer a condition, prescribe treatment, or alter any extracted value, "
        "unit, reference range, or status. Use only the supplied structured results. For out-of-range "
        "values, recommend discussing them with a licensed healthcare professional. Avoid emergency "
        "claims. Return JSON with keys keyObservations (string) and wellnessRecommendations (array of strings)."
    )
    raw = _qwen_chat(system, json.dumps(parameters, ensure_ascii=False), json_output=True)
    if not raw:
        return None
    try:
        data = json.loads(raw)
        observations = data.get("keyObservations")
        recommendations = data.get("wellnessRecommendations")
        if not isinstance(observations, str) or not isinstance(recommendations, list):
            return None
        recommendations = [item.strip() for item in recommendations if isinstance(item, str) and item.strip()]
        if any((item.get("status") or "").upper() in {"HIGH", "LOW"} for item in parameters):
            if not any("doctor" in item.lower() or "healthcare professional" in item.lower() for item in recommendations):
                recommendations.insert(0, "Discuss out-of-range results with a licensed healthcare professional.")
        return {"keyObservations": observations.strip(), "wellnessRecommendations": recommendations[:5]}
    except (ValueError, TypeError):
        logger.info("Qwen returned an invalid report summary; using built-in guidance")
        return None

DISCLAIMER_TEXT = (
    "This AI health insight is for informational and educational wellness purposes only. "
    "It does not constitute a medical diagnosis, clinical evaluation, or prescription. "
    "Always consult a licensed healthcare professional or physician regarding abnormal test results "
    "or medical conditions."
)

# Clinical biomarker knowledge base
BIOMARKER_KNOWLEDGE = {
    "hemoglobin": {
        "name": "Hemoglobin",
        "description": "An essential protein in your red blood cells that carries oxygen from your lungs throughout your entire body.",
        "normal": "Your hemoglobin level is within the healthy reference range, indicating adequate oxygen-carrying capacity in your blood.",
        "low": "Your hemoglobin level is lower than the reference interval. Low levels can be associated with fatigue or reduced oxygen delivery.",
        "high": "Your hemoglobin level is above the reference interval. Higher levels can occur with dehydration, smoking, or living at higher altitudes.",
        "wellness_tips": [
            "Include iron-rich foods in your diet, such as spinach, lentils, beans, and seeds.",
            "Pair iron sources with vitamin C rich foods (citrus fruits, bell peppers) to boost absorption.",
            "Stay well-hydrated throughout the day."
        ]
    },
    "fasting blood sugar": {
        "name": "Fasting Blood Sugar",
        "description": "Measures the concentration of glucose in your bloodstream after an overnight fast (typically 8–10 hours).",
        "normal": "Your fasting glucose is in the optimal reference range, indicating healthy carbohydrate metabolism.",
        "low": "Your fasting glucose is below the reference interval. Low blood sugar can cause shakiness, dizziness, or fatigue.",
        "high": "Your fasting glucose is higher than the reference interval. Elevated glucose indicates a need to review carbohydrate intake and metabolic health.",
        "wellness_tips": [
            "Focus on complex carbohydrates with high fiber (oats, legumes, whole grains).",
            "Engage in post-meal light walking (10-15 minutes) to support glucose regulation.",
            "Limit refined sugars and sweetened beverages."
        ]
    },
    "glucose": {
        "name": "Blood Glucose",
        "description": "The primary sugar found in your blood that serves as the main source of energy for your cells.",
        "normal": "Your blood glucose level is within the standard reference range.",
        "low": "Your blood glucose level is lower than the reference interval.",
        "high": "Your blood glucose level is elevated above the reference interval.",
        "wellness_tips": [
            "Balance meals with adequate protein, healthy fats, and dietary fiber.",
            "Maintain consistent meal timings."
        ]
    },
    "hba1c": {
        "name": "Glycated Hemoglobin (HbA1c)",
        "description": "Reflects your average blood sugar levels over the past 2 to 3 months.",
        "normal": "Your HbA1c is within the healthy reference range, demonstrating consistent long-term blood sugar balance.",
        "low": "Your HbA1c is below the standard range, reflecting lower average blood sugar over recent months.",
        "high": "Your HbA1c is elevated above the standard range, indicating higher average blood glucose over the past 90 days.",
        "wellness_tips": [
            "Adopt regular aerobic exercise (brisk walking, cycling, swimming) for 150 minutes per week.",
            "Prioritize whole, unprocessed foods and minimize added sugars."
        ]
    },
    "total cholesterol": {
        "name": "Total Cholesterol",
        "description": "The total amount of cholesterol in your blood, including both HDL ('good') and LDL ('bad') cholesterol.",
        "normal": "Your total cholesterol is within the desirable reference range for cardiovascular health.",
        "low": "Your total cholesterol is below the reference interval. Cholesterol is needed for cell membranes and hormone synthesis.",
        "high": "Your total cholesterol is higher than the desirable limit, suggesting an opportunity to support heart-healthy habits.",
        "wellness_tips": [
            "Incorporate heart-healthy fats such as extra virgin olive oil, nuts, and avocados.",
            "Increase soluble fiber from oats, flaxseeds, and apples.",
            "Stay physically active with regular cardiovascular exercise."
        ]
    },
    "hdl cholesterol": {
        "name": "HDL Cholesterol ('Good' Cholesterol)",
        "description": "High-Density Lipoprotein helps transport excess cholesterol from your bloodstream back to your liver for clearance.",
        "normal": "Your HDL cholesterol level is healthy, providing protective benefits for your cardiovascular system.",
        "low": "Your HDL cholesterol is below the recommended target. Higher HDL levels are generally protective for heart wellness.",
        "high": "Your HDL cholesterol is robust and favorable, supporting healthy vascular health.",
        "wellness_tips": [
            "Regular aerobic exercise is one of the most effective lifestyle habits to help boost HDL levels.",
            "Include omega-3 fatty acids from walnuts, chia seeds, and fatty fish."
        ]
    },
    "ldl cholesterol": {
        "name": "LDL Cholesterol ('Bad' Cholesterol)",
        "description": "Low-Density Lipoprotein carries cholesterol to your tissues. High levels can accumulate in blood vessel walls.",
        "normal": "Your LDL cholesterol is within the optimal reference range, supporting healthy blood vessels.",
        "low": "Your LDL cholesterol is low, which is generally favorable for cardiovascular wellness.",
        "high": "Your LDL cholesterol is above the desirable reference threshold. Elevated LDL warrants focus on dietary and lifestyle modifications.",
        "wellness_tips": [
            "Replace saturated and trans fats with unsaturated plant-based oils.",
            "Consume 25–30 grams of dietary fiber daily, especially soluble fiber."
        ]
    },
    "triglycerides": {
        "name": "Triglycerides",
        "description": "A common type of fat (lipid) found in your blood that stores unused calories as energy.",
        "normal": "Your triglyceride level is within the normal reference range.",
        "low": "Your triglyceride level is low, indicating minimal circulating lipid fats.",
        "high": "Your triglyceride level is elevated above the reference range. High triglycerides are often related to excess simple carbohydrates, alcohol, or inactivity.",
        "wellness_tips": [
            "Reduce consumption of sugary snacks, juices, and refined flour.",
            "Engage in regular strength training and brisk walking.",
            "Ensure regular hydration and minimize alcohol intake."
        ]
    },
    "vitamin d3": {
        "name": "Vitamin D (25-OH)",
        "description": "A vital fat-soluble hormone essential for bone density, immune function, mood regulation, and muscle performance.",
        "normal": "Your Vitamin D level is sufficient, supporting healthy bones and optimal immune defenses.",
        "low": "Your Vitamin D level is below the recommended threshold (insufficiency/deficiency). This is common with limited sun exposure.",
        "high": "Your Vitamin D level is elevated above the standard reference range.",
        "wellness_tips": [
            "Aim for 15-20 minutes of safe morning sunlight exposure on arms and legs.",
            "Include Vitamin D fortified foods and foods like egg yolks and mushrooms in your meals.",
            "Consult a physician or registered dietitian regarding appropriate supplementation."
        ]
    },
    "vitamin b12": {
        "name": "Vitamin B12",
        "description": "Crucial for nervous system function, DNA synthesis, and red blood cell production.",
        "normal": "Your Vitamin B12 level is in the healthy range, supporting energy metabolism and nervous system health.",
        "low": "Your Vitamin B12 level is lower than the reference interval. Low B12 can contribute to fatigue, numbness, or tingling.",
        "high": "Your Vitamin B12 level is above the standard reference range.",
        "wellness_tips": [
            "For vegetarians and vegans, consume B12-fortified plant milks, nutritional yeast, or discuss a B12 supplement with a doctor.",
            "Ensure adequate stomach acid and gut health to support vitamin absorption."
        ]
    },
    "tsh": {
        "name": "Thyroid Stimulating Hormone (TSH)",
        "description": "Produced by the pituitary gland to regulate your thyroid gland's production of metabolic hormones.",
        "normal": "Your TSH level is in the normal reference interval, suggesting balanced thyroid signaling.",
        "low": "Your TSH level is lower than the reference range, which can occur when thyroid hormone levels are active.",
        "high": "Your TSH level is above the reference range, indicating that the pituitary gland is sending extra signals to stimulate thyroid activity.",
        "wellness_tips": [
            "Ensure adequate intake of dietary iodine (iodized salt, sea vegetables) and selenium (brazil nuts).",
            "Prioritize stress-reducing practices, as chronic stress impacts thyroid hormone conversion.",
            "Maintain consistent, restful sleep routines."
        ]
    },
    "serum creatinine": {
        "name": "Serum Creatinine",
        "description": "A natural waste product generated by normal muscle metabolism and cleared by healthy kidneys.",
        "normal": "Your serum creatinine is within the standard reference range, indicating normal renal filtration function.",
        "low": "Your serum creatinine is below the reference range, which can occur with lower muscle mass.",
        "high": "Your serum creatinine is above the reference interval. Elevated creatinine can be influenced by dehydration, intense exercise, or reduced kidney clearance.",
        "wellness_tips": [
            "Drink plenty of water throughout the day (at least 2.5–3 liters unless medically restricted).",
            "Avoid excessive use of over-the-counter NSAID pain medications without consulting a physician."
        ]
    },
    "wbc": {
        "name": "Total White Blood Cells (WBC / TLC)",
        "description": "Immune cells responsible for defending your body against infections and inflammation.",
        "normal": "Your white blood cell count is within the healthy reference range, reflecting a balanced immune state.",
        "low": "Your white blood cell count is below the reference interval, which can occur during viral recovery or reduced immune activity.",
        "high": "Your white blood cell count is elevated above the reference range. Elevations typically occur with physical stress, inflammation, or immune activation.",
        "wellness_tips": [
            "Support your immune health with antioxidant-rich berries, citrus, garlic, and leafy greens.",
            "Ensure 7–8 hours of restorative sleep every night to support immune cell regeneration."
        ]
    },
    "platelet": {
        "name": "Platelet Count",
        "description": "Small blood cells crucial for blood clotting and wound healing.",
        "normal": "Your platelet count is within the standard reference interval, indicating healthy blood clotting ability.",
        "low": "Your platelet count is lower than the reference interval. Monitor for easy bruising or bleeding.",
        "high": "Your platelet count is higher than the reference interval. Mild elevations can be reactive to inflammation or iron levels.",
        "wellness_tips": [
            "Eat a nutrient-dense diet containing folate, vitamin B12, and vitamin K (broccoli, dark leafy vegetables).",
            "Avoid smoking and stay adequately hydrated."
        ]
    }
}


def find_biomarker_key(param_name: str) -> Optional[str]:
    """Finds the best matching biomarker key in our clinical dictionary."""
    clean_name = param_name.lower().strip()
    # Direct match
    if clean_name in BIOMARKER_KNOWLEDGE:
        return clean_name

    # Partial substring match
    for key in BIOMARKER_KNOWLEDGE:
        if key in clean_name or clean_name in key:
            return key

    # Aliases
    if "hb" in clean_name and "a1c" not in clean_name:
        return "hemoglobin"
    if "fbs" in clean_name or "fasting" in clean_name:
        return "fasting blood sugar"
    if "cholesterol" in clean_name and "total" in clean_name:
        return "total cholesterol"
    if "hdl" in clean_name:
        return "hdl cholesterol"
    if "ldl" in clean_name:
        return "ldl cholesterol"
    if "tg" in clean_name or "triglyceride" in clean_name:
        return "triglycerides"
    if "vit" in clean_name and "d" in clean_name:
        return "vitamin d3"
    if "vit" in clean_name and ("b12" in clean_name or "b-12" in clean_name):
        return "vitamin b12"
    if "creatinine" in clean_name:
        return "serum creatinine"
    if "tlc" in clean_name or "white blood" in clean_name:
        return "wbc"
    if "platelet" in clean_name:
        return "platelet"

    return None


def generate_parameter_explanation(param: Dict[str, Any]) -> Dict[str, Any]:
    """
    Generates a simple, evidence-based AI explanation and wellness tips for an extracted parameter.
    Does not hallucinate or guess missing values.
    Uses the exact reference range from the report.
    """
    name = param.get("parameter", "Biomarker")
    value = param.get("value")
    unit = param.get("unit", "")
    reference = param.get("referenceRange", "")
    status = (param.get("status") or "UNKNOWN").upper()

    key = find_biomarker_key(name)
    val_str = f"{value} {unit}".strip()
    ref_str = f"(Reference range: {reference})" if reference else ""

    if key and key in BIOMARKER_KNOWLEDGE:
        info = BIOMARKER_KNOWLEDGE[key]
        description = info["description"]
        if status == "HIGH":
            interpretation = f"{info['high']} Your result is {val_str} {ref_str}."
        elif status == "LOW":
            interpretation = f"{info['low']} Your result is {val_str} {ref_str}."
        elif status == "UNKNOWN":
            interpretation = f"Your {name} result is {val_str}, but it cannot be compared because the reference interval was not readable in the report."
        else:
            interpretation = f"{info['normal']} Your result is {val_str} {ref_str}."
        wellness_tips = info.get("wellness_tips", []) if status != "UNKNOWN" else ["Check the original report for the reference interval and discuss unclear results with your clinician."]
    else:
        description = f"Measures the concentration of {name} in your laboratory specimen."
        if status == "HIGH":
            interpretation = f"Your {name} of {val_str} is elevated above the printed reference interval {ref_str}."
        elif status == "LOW":
            interpretation = f"Your {name} of {val_str} is lower than the printed reference interval {ref_str}."
        elif status == "UNKNOWN":
            interpretation = f"Your {name} result is {val_str}, but it cannot be compared because the reference interval was not readable in the report."
        else:
            interpretation = f"Your {name} of {val_str} is within the laboratory's standard reference interval {ref_str}."

        wellness_tips = [
            "Maintain consistent daily hydration and balanced nutrition.",
            "Check the original report for the reference interval and discuss unclear results with your clinician."
        ]

    return {
        "parameter": name,
        "value": value,
        "unit": unit,
        "referenceRange": reference,
        "status": status,
        "description": description,
        "interpretation": interpretation,
        "wellnessTips": wellness_tips
    }


def generate_report_ai_summary(parameters: List[Dict[str, Any]], report_name: str) -> Dict[str, Any]:
    """
    Generates an overarching AI assessment for the full lab report:
    - Counts normal vs abnormal/high/low parameters.
    - Synthesizes key observations.
    - Provides general wellness suggestions based solely on extracted values.
    - Includes medical disclaimers.
    """
    total = len(parameters)
    if total == 0:
        return {
            "totalParameters": 0,
            "normalCount": 0,
            "outOfRangeCount": 0,
            "unknownCount": 0,
            "keyObservations": "No valid laboratory parameters were recognized from this document.",
            "wellnessRecommendations": [],
            "disclaimer": DISCLAIMER_TEXT
        }

    normal_params = []
    high_params = []
    low_params = []
    unknown_params = []

    for p in parameters:
        status = (p.get("status") or "UNKNOWN").upper()
        if status == "HIGH":
            high_params.append(p)
        elif status == "LOW":
            low_params.append(p)
        elif status == "NORMAL":
            normal_params.append(p)
        else:
            unknown_params.append(p)

    out_of_range_count = len(high_params) + len(low_params)

    observations = []
    if out_of_range_count == 0 and not unknown_params:
        observations.append(f"All {total} recognized laboratory parameters are within their standard reference intervals.")
    else:
        observations.append(
            f"Of {total} extracted biomarkers, {len(normal_params)} are within standard reference ranges, "
            f"{out_of_range_count} deviate from printed intervals, and {len(unknown_params)} could not be assessed against a readable interval."
        )
        if high_params:
            names = ", ".join(p["parameter"] for p in high_params)
            observations.append(f"Values above reference range: {names}.")
        if low_params:
            names = ", ".join(p["parameter"] for p in low_params)
            observations.append(f"Values below reference range: {names}.")

    # Collect wellness tips based strictly on out of range parameters
    tips = set()
    for p in high_params + low_params:
        key = find_biomarker_key(p.get("parameter", ""))
        if key and key in BIOMARKER_KNOWLEDGE:
            for tip in BIOMARKER_KNOWLEDGE[key].get("wellness_tips", []):
                tips.add(tip)

    if not tips:
        tips.add("Maintain daily hydration (2.5L to 3L water per day).")
        tips.add("Prioritize 7-8 hours of quality sleep and regular physical movement.")
        tips.add("Eat a balanced variety of whole foods, fresh vegetables, and lean proteins.")

    qwen_summary = _qwen_report_summary(parameters)
    return {
        "reportName": report_name,
        "totalParameters": total,
        "normalCount": len(normal_params),
        "outOfRangeCount": out_of_range_count,
        "unknownCount": len(unknown_params),
        "highCount": len(high_params),
        "lowCount": len(low_params),
        # Keep counts and range observations deterministic; Qwen only adds guidance.
        "keyObservations": " ".join(observations),
        "wellnessRecommendations": qwen_summary["wellnessRecommendations"] if qwen_summary else list(tips)[:5],
        "disclaimer": DISCLAIMER_TEXT
    }


def answer_wellness_question(question: str, parameters: List[Dict[str, Any]]) -> str:
    """
    Answers user questions about their lab report in simple language,
    referencing actual extracted parameters, distinguishing normal/high/low,
    and enforcing medical safety guardrails.
    """
    q_lower = question.lower().strip()

    # Check if question is asking for diagnosis or prescription
    if any(word in q_lower for word in ["diagnose", "diagnosis", "disease", "medicine", "pill", "drug", "cure", "prescribe"]):
        return (
            "As an AI Wellness Assistant, I cannot provide medical diagnoses, prescribe medicines, or replace a doctor's care. "
            "Based on your report, I can help you understand what your biomarkers mean and suggest lifestyle or dietary habits. "
            "Please consult your physician for clinical diagnosis and medical treatments. "
            + DISCLAIMER_TEXT
        )

    qwen_answer = _qwen_chat(
        "You are My Wellness, a friendly conversational wellness assistant powered by Qwen. Respond "
        "naturally to greetings such as hi or hello and answer general questions even when no lab results "
        "are supplied. If the user asks about their report but no results are available, say you cannot "
        "see a report yet and invite them to upload one. Use supplied lab values only when discussing "
        "specific results. Do not diagnose, claim the user has a disease, prescribe or change medication, "
        "or invent lab values. Explain that lab results need clinical context and recommend discussing "
        "out-of-range results with a licensed healthcare professional. Keep the response helpful and "
        "concise, then add a brief educational disclaimer.",
        json.dumps({"question": question, "parameters": parameters}, ensure_ascii=False),
    )
    if qwen_answer:
        return qwen_answer + "\n\n" + DISCLAIMER_TEXT
    return (
        f"I couldn't reach Qwen ({QWEN_MODEL}) through Ollama at {OLLAMA_URL}. "
        "Start Ollama, confirm the model is installed, then check the OCR service at "
        "http://localhost:8000/health.\n\n"
        f"{DISCLAIMER_TEXT}"
    )
