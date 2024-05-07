# XBeats: A Real-Time Electrocardiogram Monitoring and Analysis System Readme

## Project Title
XBeats: A Real-Time Electrocardiogram Monitoring and Analysis System

## Description
This repository contains the codebase for a novel platform for real-time electrocardiogram monitoring and analysis that uses edge computing and machine learning for early anomaly detection. The platform encompasses a data acquisition ECG patch with 12 leads to collect heart signals, perform on-chip processing, and transmit the data to healthcare providers in real-time for further analysis. The ECG patch provides a dynamically configurable selection of the active ECG leads that could be transmitted to the backend monitoring system. The selection ranges from a single ECG lead to a complete 12-lead ECG testing configuration. XBeats implements a lightweight binary classifier for early anomaly detection to reduce the time to action should abnormal heart conditions occur. This initial detection phase is performed on the edge (i.e., the device paired with the patch) and alerts can be configured to notify designated healthcare providers. Further deep analysis can be performed on the full fidelity 12-lead data sent to the backend. A fully functional prototype of the XBeats has been implemented to demonstrate the feasibly and usability of the proposed system. Performance evaluation shows that XBeats can achieve up to 95.30% detection accuracy for abnormal conditions, while maintaining a high data acquisition rate of up to 441 samples per second. Moreover, the analytical results of the energy consumption profile show that the ECG patch provides up to 37 h of continuous 12-lead ECG streaming.

## Installation
1. Clone the repository to your local machine:

git clone https://github.com/Badr1600/RxAndroidBleforECG.git

2. Then run the code using your preferred Android IDE

## Usage
Please refere to our publication: Badr, A.; Badawi, A.; Rashwan, A.; Elgazzar, K. XBeats: A Real-Time Electrocardiogram Monitoring and Analysis System. Signals 2022, 3, 189-208. https://doi.org/10.3390/signals3020013

## Contributing
We welcome contributions from the community to improve this project. If you would like to contribute, please follow these steps:
1. Fork the repository
2. Create a new branch (`git checkout -b feature/improvement`)
3. Make your changes and commit them (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/improvement`)
5. Create a new Pull Request

## Citation
If you use this code in your research work, we kindly ask that you cite the following paper:

Badr, A.; Badawi, A.; Rashwan, A.; Elgazzar, K. XBeats: A Real-Time Electrocardiogram Monitoring and Analysis System. Signals 2022, 3, 189-208. https://doi.org/10.3390/signals3020013

## Maintainers
* Ahmed Badr (github: badr1600)

## Contact
If you have any questions or feedback, please don't hesitate to contact us at ahmed.badr@ontariotechu.net.

## Acknowledgements
This codebase was forked from RxAndroidBle, which can be found at https://github.com/dariuszseweryn/RxAndroidBle. We extend our sincere thanks to the original authors and contributors for their valuable work and contributions.

This codebase was developed at IoT Research Lab, Ontario Tech University. We extend our sincere thanks to Dr. Khalid Elgazzar and all members of the lab for their support and valuable contributions.
